package com.ollamacraft.plugin.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ollamacraft.plugin.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Service for intelligent response detection
 * Determines whether the AI should respond to a chat message
 */
public class ResponseDetectionService {
    
    private final Logger logger;
    private final AIProvider detectionProvider;
    private String agentName;
    
    // Configuration
    private boolean intelligentDetectionEnabled;
    private boolean fallbackToPrefix;
    private String triggerPrefix;
    private int detectionTimeoutSeconds;
    
    // Patterns for quick detection
    private Pattern directMentionPattern;
    private Pattern questionPattern;
    private Pattern helpPattern;
    private Pattern greetingPattern;
    
    public ResponseDetectionService(Logger logger, AIProvider detectionProvider, String agentName) {
        this.logger = logger;
        this.detectionProvider = detectionProvider;
        this.agentName = agentName;
        
        // Default configuration
        this.intelligentDetectionEnabled = true;
        this.fallbackToPrefix = true;
        this.triggerPrefix = agentName + ", ";
        this.detectionTimeoutSeconds = 5;
        
        initializePatterns();
    }
    
    /**
     * Initialize regex patterns for quick detection
     */
    private void initializePatterns() {
        // Case-insensitive patterns
        String name = Pattern.quote(agentName.toLowerCase());
        
        this.directMentionPattern = Pattern.compile(
            "(?i)\\b" + name + "\\b|@" + name + "\\b|hey\\s+" + name + "\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        this.questionPattern = Pattern.compile(
            "\\?|\\bhow\\b|\\bwhat\\b|\\bwhere\\b|\\bwhen\\b|\\bwhy\\b|\\bwho\\b|\\bcan you\\b|\\bwill you\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        this.helpPattern = Pattern.compile(
            "\\bhelp\\b|\\bassist\\b|\\bsupport\\b|\\bproblem\\b|\\bissue\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        this.greetingPattern = Pattern.compile(
            "\\bhello\\b|\\bhi\\b|\\bhey\\b|\\bgood morning\\b|\\bgood afternoon\\b|\\bgood evening\\b",
            Pattern.CASE_INSENSITIVE
        );
    }
    
    /**
     * Configure the detection service
     * @param config Configuration map
     */
    public void configure(JsonObject config) {
        if (config.has("intelligent-detection")) {
            this.intelligentDetectionEnabled = config.get("intelligent-detection").getAsBoolean();
        }
        if (config.has("fallback-to-prefix")) {
            this.fallbackToPrefix = config.get("fallback-to-prefix").getAsBoolean();
        }
        if (config.has("trigger-prefix")) {
            this.triggerPrefix = config.get("trigger-prefix").getAsString();
        }
        if (config.has("detection-timeout-seconds")) {
            this.detectionTimeoutSeconds = config.get("detection-timeout-seconds").getAsInt();
        }
        
        logger.info("ResponseDetectionService configured - Intelligent: " + intelligentDetectionEnabled + 
                   ", Prefix: " + triggerPrefix);
    }
    
    /**
     * Determine if AI should respond to a message
     * @param message The chat message
     * @param playerName Name of the player who sent the message
     * @param recentMessages Recent chat context
     * @return CompletableFuture<Boolean> indicating whether to respond
     */
    public CompletableFuture<Boolean> shouldRespond(String message, String playerName, List<ChatMessage> recentMessages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Quick prefix check (highest priority)
                if (message.toLowerCase().startsWith(triggerPrefix.toLowerCase())) {
                    debug("Message starts with trigger prefix, responding");
                    return true;
                }
                
                // Quick pattern checks
                boolean hasDirectMention = directMentionPattern.matcher(message).find();
                boolean hasQuestion = questionPattern.matcher(message).find();
                boolean hasHelpRequest = helpPattern.matcher(message).find();
                boolean hasGreeting = greetingPattern.matcher(message).find();
                
                // High confidence quick checks
                if (hasDirectMention && (hasQuestion || hasHelpRequest)) {
                    debug("Direct mention with question/help request, responding");
                    return true;
                }
                
                if (hasDirectMention && message.length() < 50) {
                    debug("Short direct mention, responding");
                    return true;
                }
                
                // If intelligent detection is disabled, use fallback logic
                if (!intelligentDetectionEnabled) {
                    return useSimpleDetection(message, hasDirectMention, hasQuestion, hasHelpRequest, hasGreeting);
                }
                
                // Use AI for intelligent detection
                return performIntelligentDetection(message, playerName, recentMessages);
                
            } catch (Exception e) {
                warn("Error in response detection: " + e.getMessage());
                
                // Fallback to simple logic on error
                if (fallbackToPrefix) {
                    return message.toLowerCase().startsWith(triggerPrefix.toLowerCase());
                }
                return false;
            }
        });
    }
    
    /**
     * Simple rule-based detection
     */
    private boolean useSimpleDetection(String message, boolean hasDirectMention, 
                                     boolean hasQuestion, boolean hasHelpRequest, boolean hasGreeting) {
        // Simple rules for when intelligent detection is disabled
        if (hasDirectMention) {
            debug("Simple detection: Direct mention found, responding");
            return true;
        }
        
        if (hasQuestion && message.length() > 10) {
            debug("Simple detection: Question detected, responding");
            return true;
        }
        
        if (hasHelpRequest) {
            debug("Simple detection: Help request detected, responding");
            return true;
        }
        
        if (hasGreeting && message.length() < 30) {
            debug("Simple detection: Short greeting detected, responding");
            return true;
        }
        
        debug("Simple detection: No triggers found, not responding");
        return false;
    }
    
    /**
     * Use AI to intelligently determine if response is needed
     */
    private boolean performIntelligentDetection(String message, String playerName, List<ChatMessage> recentMessages) {
        try {
            // Build detection prompt
            String detectionPrompt = buildDetectionPrompt(message, playerName, recentMessages);
            
            // Create simple messages for detection
            List<ChatMessage> detectionMessages = new ArrayList<>();
            detectionMessages.add(new ChatMessage("user", detectionPrompt));
            
            // Use the detection provider (should be lightweight/fast)
            CompletableFuture<AIResponse> future = detectionProvider.chat(detectionMessages, 
                "You are a smart chat filter. Respond ONLY with 'YES' or 'NO' to indicate if the AI assistant should respond.");
            
            AIResponse response = future.get(detectionTimeoutSeconds, TimeUnit.SECONDS);
            
            if (response.isSuccessful()) {
                String aiDecision = response.getContent().trim().toUpperCase();
                boolean shouldRespond = aiDecision.contains("YES");
                
                debug("Intelligent detection decision: " + aiDecision + " -> " + shouldRespond);
                return shouldRespond;
            } else {
                warn("Intelligent detection failed: " + response.getErrorMessage());
                return useSimpleDetection(message, 
                    directMentionPattern.matcher(message).find(),
                    questionPattern.matcher(message).find(),
                    helpPattern.matcher(message).find(),
                    greetingPattern.matcher(message).find());
            }
            
        } catch (Exception e) {
            warn("Intelligent detection error: " + e.getMessage());
            // Fallback to simple detection
            return useSimpleDetection(message, 
                directMentionPattern.matcher(message).find(),
                questionPattern.matcher(message).find(),
                helpPattern.matcher(message).find(),
                greetingPattern.matcher(message).find());
        }
    }
    
    /**
     * Build detection prompt for AI
     */
    private String buildDetectionPrompt(String message, String playerName, List<ChatMessage> recentMessages) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Analyze this Minecraft chat message and determine if the AI assistant '")
              .append(agentName)
              .append("' should respond.\n\n");
        
        prompt.append("Rules:\n");
        prompt.append("- Respond YES if: message mentions ").append(agentName)
              .append(", asks a question, requests help, or seems directed at an AI assistant\n");
        prompt.append("- Respond NO if: casual player chat, commands, or conversation between players\n");
        prompt.append("- Consider context from recent messages\n\n");
        
        // Add recent context (last 3 messages)
        if (!recentMessages.isEmpty()) {
            prompt.append("Recent context:\n");
            int contextCount = Math.min(3, recentMessages.size());
            for (int i = recentMessages.size() - contextCount; i < recentMessages.size(); i++) {
                ChatMessage recent = recentMessages.get(i);
                if (!"system".equals(recent.getRole())) {
                    prompt.append("- ").append(recent.getContent()).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        prompt.append("Current message from ").append(playerName).append(": \"")
              .append(message).append("\"\n\n");
        
        prompt.append("Should ").append(agentName).append(" respond? Answer only YES or NO.");
        
        return prompt.toString();
    }
    
    /**
     * Check if message is explicitly directed at agent
     * @param message Chat message
     * @return true if explicitly directed
     */
    public boolean isExplicitlyDirected(String message) {
        // Check for prefix
        if (message.toLowerCase().startsWith(triggerPrefix.toLowerCase())) {
            return true;
        }
        
        // Check for direct mention patterns
        return directMentionPattern.matcher(message).find();
    }
    
    /**
     * Get response confidence score (0.0 to 1.0)
     * @param message Chat message
     * @return Confidence score
     */
    public double getResponseConfidence(String message) {
        double confidence = 0.0;
        
        // Prefix gives highest confidence
        if (message.toLowerCase().startsWith(triggerPrefix.toLowerCase())) {
            return 1.0;
        }
        
        // Direct mention
        if (directMentionPattern.matcher(message).find()) {
            confidence += 0.7;
        }
        
        // Question indicators
        if (questionPattern.matcher(message).find()) {
            confidence += 0.3;
        }
        
        // Help indicators
        if (helpPattern.matcher(message).find()) {
            confidence += 0.4;
        }
        
        // Greeting indicators
        if (greetingPattern.matcher(message).find()) {
            confidence += 0.2;
        }
        
        // Length factor (shorter messages with mentions are more likely directed)
        if (confidence > 0.5 && message.length() < 50) {
            confidence += 0.2;
        }
        
        return Math.min(1.0, confidence);
    }
    
    /**
     * Update agent name and reinitialize patterns
     * @param newAgentName New agent name
     */
    public void updateAgentName(String newAgentName) {
        this.agentName = newAgentName;
        this.triggerPrefix = newAgentName + ", ";
        initializePatterns();
        logger.info("Updated agent name to: " + newAgentName);
    }
    
    /**
     * Get current configuration as JSON
     * @return Configuration JSON object
     */
    public JsonObject getConfiguration() {
        JsonObject config = new JsonObject();
        config.addProperty("agent-name", agentName);
        config.addProperty("trigger-prefix", triggerPrefix);
        config.addProperty("intelligent-detection", intelligentDetectionEnabled);
        config.addProperty("fallback-to-prefix", fallbackToPrefix);
        config.addProperty("detection-timeout-seconds", detectionTimeoutSeconds);
        return config;
    }
    
    /**
     * Get detection statistics
     * @return Statistics string
     */
    public String getStatistics() {
        return "ResponseDetectionService - Agent: " + agentName + 
               ", Intelligent: " + intelligentDetectionEnabled +
               ", Timeout: " + detectionTimeoutSeconds + "s";
    }
    
    private void debug(String message) {
        logger.info("[ResponseDetection] " + message);
    }
    
    private void warn(String message) {
        logger.warning("[ResponseDetection] " + message);
    }
}