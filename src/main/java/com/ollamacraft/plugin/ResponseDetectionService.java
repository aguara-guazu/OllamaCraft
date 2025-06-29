package com.ollamacraft.plugin;

import com.ollamacraft.plugin.config.AIConfiguration;
import com.ollamacraft.plugin.model.ChatMessage;
import com.ollamacraft.plugin.provider.AIProvider;
import com.ollamacraft.plugin.provider.AIProviderFactory;
import com.ollamacraft.plugin.provider.AIResponse;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Intelligent response detection service for OllamaCraft
 * Provides multi-level analysis to determine if the AI should respond to player messages
 */
public class ResponseDetectionService {
    
    private final Logger logger;
    private final AIConfiguration config;
    private final AIProviderFactory providerFactory;
    
    // Pattern-based detection patterns
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        ".*[¿?].*|\\b(how|what|when|where|why|who|which|can you|could you|would you|will you|do you|are you|is there|help me|ayuda|ayúdame|cómo|qué|cuándo|dónde|por qué|quién|puedes|podrías)\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern GREETING_PATTERN = Pattern.compile(
        "\\b(hello|hi|hey|hola|buenos días|buenas tardes|buenas noches|good morning|good afternoon|good evening|sup|wassup|greetings|salutations)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern HELP_PATTERN = Pattern.compile(
        "\\b(help|ayuda|assist|assistance|support|apoyo|necesito ayuda|need help|can you help|puedes ayudar)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern COMMANDS_PATTERN = Pattern.compile(
        "^/[a-zA-Z0-9_-]+.*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SPAM_PATTERN = Pattern.compile(
        "^[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?~`]+$|^\\s*$|^(.+)\\1{3,}$",
        Pattern.CASE_INSENSITIVE
    );
    
    // Decision cache for similar messages
    private final Map<String, CachedDecision> decisionCache = new ConcurrentHashMap<>();
    
    // Recent conversation context for each player
    private final Map<String, List<ChatMessage>> playerContext = new ConcurrentHashMap<>();
    
    // AI provider for contextual analysis
    private AIProvider detectionProvider;
    
    /**
     * Cached decision with timestamp
     */
    private static class CachedDecision {
        final boolean shouldRespond;
        final double confidence;
        final Instant timestamp;
        final String reason;
        
        CachedDecision(boolean shouldRespond, double confidence, String reason) {
            this.shouldRespond = shouldRespond;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = Instant.now();
        }
        
        boolean isExpired(int cacheMinutes) {
            return timestamp.plus(cacheMinutes, ChronoUnit.MINUTES).isBefore(Instant.now());
        }
    }
    
    /**
     * Detection result with confidence and reasoning
     */
    public static class DetectionResult {
        private final boolean shouldRespond;
        private final double confidence;
        private final String reason;
        private final String method;
        
        public DetectionResult(boolean shouldRespond, double confidence, String reason, String method) {
            this.shouldRespond = shouldRespond;
            this.confidence = confidence;
            this.reason = reason;
            this.method = method;
        }
        
        public boolean shouldRespond() { return shouldRespond; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
        public String getMethod() { return method; }
    }
    
    public ResponseDetectionService(AIConfiguration config, AIProviderFactory providerFactory, Logger logger) {
        this.config = config;
        this.providerFactory = providerFactory;
        this.logger = logger;
        initializeDetectionProvider();
    }
    
    /**
     * Initialize the AI provider for contextual detection
     */
    private void initializeDetectionProvider() {
        try {
            if (config.isIntelligentDetection()) {
                String providerName = config.getDetectionProvider();
                Map<String, Object> providerConfig = config.getProviderConfiguration(providerName);
                
                if (providerConfig != null && !providerConfig.isEmpty()) {
                    this.detectionProvider = providerFactory.createProvider(providerName, providerConfig);
                    logger.info("Initialized detection provider: " + providerName);
                } else {
                    logger.warning("No configuration found for detection provider: " + providerName + 
                                 ", falling back to pattern detection only");
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize detection provider: " + e.getMessage() + 
                         ", falling back to pattern detection only");
        }
    }
    
    /**
     * Analyze a player message to determine if the AI should respond
     * @param player The player who sent the message
     * @param message The message content
     * @return DetectionResult with decision and metadata
     */
    public CompletableFuture<DetectionResult> analyzeMessage(Player player, String message) {
        String playerId = player.getUniqueId().toString();
        String normalizedMessage = message.trim();
        
        // Quick cache check
        if (config.isIntelligentDetection() && config.isCacheDecisions()) {
            String cacheKey = generateCacheKey(normalizedMessage);
            CachedDecision cached = decisionCache.get(cacheKey);
            if (cached != null && !cached.isExpired(config.getCacheDurationMinutes())) {
                logger.fine("Using cached decision for message similarity: " + cached.reason);
                return CompletableFuture.completedFuture(
                    new DetectionResult(cached.shouldRespond, cached.confidence, cached.reason, "cached")
                );
            }
        }
        
        // Step 1: Fast pattern analysis
        DetectionResult patternResult = analyzePatterns(normalizedMessage);
        
        // If patterns give a strong indication, use it
        if (patternResult.confidence >= 0.8 || !config.isIntelligentDetection()) {
            if (config.isIntelligentDetection() && config.isCacheDecisions()) {
                cacheDecision(normalizedMessage, patternResult);
            }
            return CompletableFuture.completedFuture(patternResult);
        }
        
        // Step 2: Contextual AI analysis for ambiguous cases
        if (detectionProvider != null) {
            return analyzeWithAI(player, normalizedMessage, patternResult)
                .orTimeout(config.getDetectionTimeoutSeconds(), TimeUnit.SECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        logger.warning("AI detection analysis failed: " + throwable.getMessage() + 
                                     ", using pattern result");
                        return patternResult;
                    }
                    
                    // Cache the AI decision
                    if (config.isCacheDecisions()) {
                        cacheDecision(normalizedMessage, result);
                    }
                    return result;
                });
        }
        
        // Fallback to pattern result
        return CompletableFuture.completedFuture(patternResult);
    }
    
    /**
     * Fast pattern-based analysis
     */
    private DetectionResult analyzePatterns(String message) {
        String agentName = config.getAgentName().toLowerCase();
        String messageLower = message.toLowerCase();
        
        // Direct mention with high confidence
        if (messageLower.contains(agentName + ",") || messageLower.contains(agentName + " ") || 
            messageLower.startsWith(agentName) || messageLower.endsWith(agentName)) {
            return new DetectionResult(true, 0.95, "Direct mention of agent name", "pattern");
        }
        
        // Traditional prefix check
        if (message.startsWith(config.getTriggerPrefix())) {
            return new DetectionResult(true, 0.9, "Traditional trigger prefix detected", "pattern");
        }
        
        // Spam or commands - definite no
        if (SPAM_PATTERN.matcher(message).matches()) {
            return new DetectionResult(false, 0.95, "Message appears to be spam or nonsense", "pattern");
        }
        
        if (COMMANDS_PATTERN.matcher(message).matches()) {
            return new DetectionResult(false, 0.85, "Message is a command", "pattern");
        }
        
        // Question patterns - medium confidence yes
        if (QUESTION_PATTERN.matcher(message).matches()) {
            return new DetectionResult(true, 0.7, "Question pattern detected", "pattern");
        }
        
        // Greeting patterns - medium confidence yes
        if (GREETING_PATTERN.matcher(message).matches()) {
            return new DetectionResult(true, 0.65, "Greeting pattern detected", "pattern");
        }
        
        // Help request patterns - high confidence yes
        if (HELP_PATTERN.matcher(message).matches()) {
            return new DetectionResult(true, 0.8, "Help request pattern detected", "pattern");
        }
        
        // Default for ambiguous cases - let AI decide
        return new DetectionResult(false, 0.4, "Ambiguous message, needs contextual analysis", "pattern");
    }
    
    /**
     * AI-powered contextual analysis
     */
    private CompletableFuture<DetectionResult> analyzeWithAI(Player player, String message, DetectionResult patternResult) {
        try {
            String playerId = player.getUniqueId().toString();
            List<ChatMessage> recentContext = getRecentContext(playerId);
            
            String detectionPrompt = buildDetectionPrompt(message, player.getName(), recentContext);
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("user", detectionPrompt));
            
            return detectionProvider.chat(messages, "You are a detection system. Respond only with the requested format.")
                .thenApply(response -> parseAIDetectionResponse(response, patternResult));
                
        } catch (Exception e) {
            logger.warning("AI detection analysis failed: " + e.getMessage());
            return CompletableFuture.completedFuture(patternResult);
        }
    }
    
    /**
     * Build optimized detection prompt
     */
    private String buildDetectionPrompt(String message, String playerName, List<ChatMessage> recentContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this message from a Minecraft player and determine if the AI assistant named '")
              .append(config.getAgentName())
              .append("' should respond.\n\n");
        
        if (!recentContext.isEmpty()) {
            prompt.append("Recent conversation context:\n");
            for (ChatMessage msg : recentContext) {
                prompt.append("- ").append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Current message from player '").append(playerName).append("': ").append(message).append("\n\n");
        
        prompt.append("Respond with YES or NO and a confidence score (0.0-1.0).\n");
        prompt.append("Format: YES/NO|confidence|reason\n\n");
        
        prompt.append("Respond YES if:\n");
        prompt.append("- The player is addressing the AI assistant directly\n");
        prompt.append("- The message is a question the AI can answer\n");
        prompt.append("- The player is asking for help with the game\n");
        prompt.append("- It's a greeting or conversation directed at the AI\n");
        prompt.append("- The message relates to previous AI interactions\n\n");
        
        prompt.append("Respond NO if:\n");
        prompt.append("- It's conversation between players (not involving AI)\n");
        prompt.append("- It's a technical command or server message\n");
        prompt.append("- It's spam, nonsense, or inappropriate content\n");
        prompt.append("- The message doesn't need AI assistance\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse AI response for detection decision
     */
    private DetectionResult parseAIDetectionResponse(AIResponse response, DetectionResult fallback) {
        if (!response.isSuccessful() || !response.hasContent()) {
            logger.warning("AI detection failed: " + response.getErrorMessage());
            return fallback;
        }
        
        try {
            String content = response.getContent().trim();
            String[] parts = content.split("\\|");
            
            if (parts.length >= 2) {
                boolean shouldRespond = parts[0].trim().equalsIgnoreCase("YES");
                double confidence = Double.parseDouble(parts[1].trim());
                String reason = parts.length > 2 ? parts[2].trim() : "AI analysis";
                
                // Validate confidence range
                confidence = Math.max(0.0, Math.min(1.0, confidence));
                
                logger.fine("AI detection result: " + shouldRespond + " (confidence: " + confidence + ", reason: " + reason + ")");
                
                return new DetectionResult(shouldRespond, confidence, reason, "ai_contextual");
            }
        } catch (Exception e) {
            logger.warning("Failed to parse AI detection response: " + e.getMessage() + ", content: " + response.getContent());
        }
        
        return fallback;
    }
    
    /**
     * Get recent conversation context for a player
     */
    private List<ChatMessage> getRecentContext(String playerId) {
        List<ChatMessage> context = playerContext.get(playerId);
        if (context == null) {
            return new ArrayList<>();
        }
        
        // Return last 3 messages for context
        int startIndex = Math.max(0, context.size() - 3);
        return new ArrayList<>(context.subList(startIndex, context.size()));
    }
    
    /**
     * Add message to player context
     */
    public void addToContext(Player player, String message, boolean isAIResponse) {
        String playerId = player.getUniqueId().toString();
        List<ChatMessage> context = playerContext.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        String role = isAIResponse ? "assistant" : "user";
        context.add(new ChatMessage(role, message));
        
        // Keep only recent messages (last 10)
        if (context.size() > 10) {
            context.remove(0);
        }
    }
    
    /**
     * Cache a detection decision
     */
    private void cacheDecision(String message, DetectionResult result) {
        String cacheKey = generateCacheKey(message);
        decisionCache.put(cacheKey, new CachedDecision(
            result.shouldRespond(), result.getConfidence(), result.getReason()
        ));
        
        // Cleanup old cache entries
        if (decisionCache.size() > 100) {
            cleanupCache();
        }
    }
    
    /**
     * Generate cache key for message similarity
     */
    private String generateCacheKey(String message) {
        // Simple normalization for cache key
        String normalized = message.toLowerCase()
                                  .replaceAll("[^a-zA-Z0-9\\s]", "")
                                  .replaceAll("\\s+", " ")
                                  .trim();
        
        // Truncate for cache key
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }
        
        return normalized;
    }
    
    /**
     * Clean up expired cache entries
     */
    private void cleanupCache() {
        decisionCache.entrySet().removeIf(entry -> entry.getValue().isExpired(config.getCacheDurationMinutes()));
    }
    
    /**
     * Check if message should trigger response based on fallback rules
     */
    public boolean shouldRespondFallback(String message) {
        if (config.isMonitorAllChat()) {
            return true;
        }
        
        return message.startsWith(config.getTriggerPrefix());
    }
    
    /**
     * Get detection statistics
     */
    public String getDetectionStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Response Detection Statistics:\n");
        stats.append("- Intelligent Detection: ").append(config.isIntelligentDetection()).append("\n");
        stats.append("- Detection Provider: ").append(config.getDetectionProvider()).append("\n");
        stats.append("- Cache Entries: ").append(decisionCache.size()).append("\n");
        stats.append("- Players in Context: ").append(playerContext.size()).append("\n");
        stats.append("- Fallback Enabled: ").append(config.isFallbackToPrefix()).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Clear all caches and context
     */
    public void clearCaches() {
        decisionCache.clear();
        playerContext.clear();
        logger.info("Cleared detection caches and player context");
    }
    
    /**
     * Shutdown and cleanup resources
     */
    public void shutdown() {
        logger.info("Shutting down response detection service");
        clearCaches();
        
        if (detectionProvider != null) {
            try {
                detectionProvider.shutdown();
            } catch (Exception e) {
                logger.warning("Error shutting down detection provider: " + e.getMessage());
            }
        }
    }
}