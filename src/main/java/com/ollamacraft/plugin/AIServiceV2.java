package com.ollamacraft.plugin;

import com.google.gson.JsonObject;
import com.ollamacraft.plugin.config.AIConfiguration;
import com.ollamacraft.plugin.model.ChatMessage;
import com.ollamacraft.plugin.model.MessageHistory;
import com.ollamacraft.plugin.provider.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * New AI Service using the multi-provider architecture
 * Supports Ollama, Claude, and OpenAI providers with intelligent response detection
 */
public class AIServiceV2 {
    
    private final OllamaCraft plugin;
    private final Logger logger;
    
    // Configuration
    private AIConfiguration configuration;
    
    // Provider management
    private AIProviderFactory providerFactory;
    private AIProvider primaryProvider;
    private AIProvider detectionProvider;
    
    // Services
    private ResponseDetectionService responseDetection;
    private MessageHistory globalMessageHistory;
    private Map<String, MessageHistory> playerMessageHistories;
    
    // MCP Integration
    private MCPService mcpService;
    private MCPToolProvider toolProvider;
    private MCPToolExecutor toolExecutor;
    
    // Tool management
    private ToolConverterFactory toolConverterFactory;
    private List<JsonObject> availableTools;
    private boolean toolsEnabled;
    
    public AIServiceV2(OllamaCraft plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.playerMessageHistories = new ConcurrentHashMap<>();
        
        // Initialize configuration
        this.configuration = new AIConfiguration(plugin.getConfig(), logger);
        
        // Validate configuration
        String validationError = configuration.validateConfiguration();
        if (validationError != null) {
            logger.severe("AI Configuration validation failed: " + validationError);
            logger.severe("Please check your config.yml file");
            return;
        }
        
        // Initialize provider factory
        this.providerFactory = new AIProviderFactory(logger);
        this.toolConverterFactory = new ToolConverterFactory(logger);
        
        // Initialize message history
        this.globalMessageHistory = new MessageHistory();
        this.globalMessageHistory.setMaxHistoryLength(configuration.getMaxContextLength());
        
        // Initialize MCP service
        this.mcpService = new MCPService(plugin);
        
        // Load providers and services
        initializeProviders();
        initializeMCPIntegration();
        initializeResponseDetection();
        
        logger.info("AIServiceV2 initialized successfully with provider: " + configuration.getPrimaryProvider());
    }
    
    /**
     * Initialize AI providers
     */
    private void initializeProviders() {
        try {
            // Create primary provider
            Map<String, Object> primaryConfig = configuration.getProviderConfiguration(configuration.getPrimaryProvider());
            this.primaryProvider = providerFactory.createProvider(configuration.getPrimaryProvider(), primaryConfig);
            
            // Test primary provider connection
            primaryProvider.testConnection().thenAccept(success -> {
                if (success) {
                    logger.info("Primary provider (" + configuration.getPrimaryProvider() + ") connection test successful");
                } else {
                    logger.warning("Primary provider (" + configuration.getPrimaryProvider() + ") connection test failed");
                }
            }).exceptionally(throwable -> {
                logger.warning("Primary provider connection test error: " + throwable.getMessage());
                return null;
            });
            
            // Create detection provider (may be the same as primary)
            String detectionProviderName = configuration.getDetectionProvider();
            if (detectionProviderName.equals(configuration.getPrimaryProvider())) {
                this.detectionProvider = primaryProvider;
            } else {
                Map<String, Object> detectionConfig = configuration.getProviderConfiguration(detectionProviderName);
                this.detectionProvider = providerFactory.createProvider(detectionProviderName, detectionConfig);
            }
            
        } catch (Exception e) {
            logger.severe("Failed to initialize AI providers: " + e.getMessage());
            throw new RuntimeException("AI provider initialization failed", e);
        }
    }
    
    /**
     * Initialize MCP integration
     */
    private void initializeMCPIntegration() {
        if (!configuration.isMcpEnabled()) {
            logger.info("MCP integration disabled - AI will work with basic chat functionality only");
            this.toolsEnabled = false;
            return;
        }
        
        try {
            // Initialize MCP service
            mcpService.initialize();
            
            // Wait a moment for MCP service to be ready
            Thread.sleep(1000);
            
            if (mcpService.isRunning()) {
                // Create HTTP client for tool communication
                MCPHttpClient.MCPHttpClientOptions options = new MCPHttpClient.MCPHttpClientOptions(
                    mcpService.getServerUrl(), 
                    mcpService.getApiKey()
                )
                .debug(false)
                .logger(logger);
                
                MCPHttpClient httpClient = new MCPHttpClient(options);
                
                toolProvider = new MCPToolProvider(httpClient, logger);
                toolExecutor = new MCPToolExecutor(httpClient, toolProvider, logger);
                
                // Load available tools
                loadAvailableTools();
                
                this.toolsEnabled = true;
                logger.info("MCP integration initialized successfully");
            } else {
                logger.info("MCP bridge not running - AI will work with basic chat functionality only");
                this.toolsEnabled = false;
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize MCP integration: " + e.getMessage());
            this.toolsEnabled = false;
        }
    }
    
    /**
     * Initialize response detection service
     */
    private void initializeResponseDetection() {
        if (configuration.isIntelligentDetection()) {
            this.responseDetection = new ResponseDetectionService(logger, detectionProvider, configuration.getAgentName());
            
            // Configure detection service
            JsonObject detectionConfig = new JsonObject();
            detectionConfig.addProperty("intelligent-detection", configuration.isIntelligentDetection());
            detectionConfig.addProperty("fallback-to-prefix", configuration.isFallbackToPrefix());
            detectionConfig.addProperty("trigger-prefix", configuration.getTriggerPrefix());
            detectionConfig.addProperty("detection-timeout-seconds", configuration.getDetectionTimeoutSeconds());
            
            responseDetection.configure(detectionConfig);
            logger.info("Intelligent response detection initialized");
        } else {
            logger.info("Intelligent response detection disabled - using simple prefix detection");
        }
    }
    
    /**
     * Load available tools from MCP
     */
    private void loadAvailableTools() {
        if (toolProvider != null) {
            try {
                this.availableTools = toolProvider.getToolsForOllama().get();
                logger.info("Loaded " + availableTools.size() + " MCP tools");
            } catch (Exception e) {
                logger.warning("Failed to load MCP tools: " + e.getMessage());
                this.availableTools = new java.util.ArrayList<>();
            }
        }
    }
    
    /**
     * Process chat message and determine if AI should respond
     * @param message Chat message
     * @param playerName Player name
     * @return CompletableFuture<Boolean> indicating whether to respond
     */
    public CompletableFuture<Boolean> shouldRespond(String message, String playerName) {
        // Quick prefix check for immediate response
        if (message.toLowerCase().startsWith(configuration.getTriggerPrefix().toLowerCase())) {
            return CompletableFuture.completedFuture(true);
        }
        
        // Use intelligent detection if enabled
        if (responseDetection != null) {
            List<ChatMessage> recentMessages = getRecentMessages(playerName, 5);
            return responseDetection.shouldRespond(message, playerName, recentMessages);
        }
        
        // Fallback to simple detection
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * Generate AI response to a message
     * @param message User message
     * @param playerName Player name
     * @return CompletableFuture<String> with AI response
     */
    public CompletableFuture<String> generateResponse(String message, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Clean the message (remove trigger prefix if present)
                String cleanMessage = cleanMessage(message);
                
                // Add user message to history
                addMessageToHistory(playerName, "user", cleanMessage);
                
                // Get conversation history
                List<ChatMessage> conversationHistory = getConversationHistory(playerName);
                
                // Generate response with tools if available
                CompletableFuture<AIResponse> responseFuture;
                if (toolsEnabled && availableTools != null && !availableTools.isEmpty() && primaryProvider.supportsNativeTools()) {
                    responseFuture = primaryProvider.chatWithTools(conversationHistory, configuration.getSystemPrompt(), availableTools);
                } else {
                    responseFuture = primaryProvider.chat(conversationHistory, configuration.getSystemPrompt());
                }
                
                AIResponse aiResponse = responseFuture.get();
                
                if (aiResponse.isSuccessful()) {
                    String responseText = aiResponse.getContent();
                    
                    // Process tool calls if present
                    if (aiResponse.hasToolCalls()) {
                        responseText = processToolCalls(aiResponse.getToolCalls(), responseText, playerName);
                    }
                    
                    // Add AI response to history
                    addMessageToHistory(playerName, "assistant", responseText);
                    
                    // Format response for display
                    return formatResponse(responseText);
                } else {
                    logger.warning("AI response error: " + aiResponse.getErrorMessage());
                    return formatResponse("I'm sorry, I'm having trouble generating a response right now. Please try again.");
                }
                
            } catch (Exception e) {
                logger.severe("Error generating AI response: " + e.getMessage());
                return formatResponse("I encountered an error while processing your request. Please try again.");
            }
        });
    }
    
    /**
     * Process tool calls from AI response
     */
    private String processToolCalls(List<ToolCall> toolCalls, String responseText, String playerName) {
        if (toolExecutor == null || toolCalls.isEmpty()) {
            return responseText;
        }
        
        StringBuilder toolResults = new StringBuilder();
        if (!responseText.trim().isEmpty()) {
            toolResults.append(responseText).append("\n\n");
        }
        
        for (ToolCall toolCall : toolCalls) {
            try {
                logger.info("Executing tool: " + toolCall.getName() + " for player: " + playerName);
                
                // Convert tool call to MCP format
                JsonObject mcpToolCall = toolCall.toMCPExecutorFormat();
                
                // Execute tool
                JsonObject result = null;
                try {
                    result = toolExecutor.executeToolCall(mcpToolCall).get();
                } catch (Exception e) {
                    logger.warning("Failed to execute tool " + toolCall.getName() + ": " + e.getMessage());
                }
                
                if (result != null) {
                    String resultText = formatToolResult(toolCall.getName(), result);
                    toolResults.append(resultText);
                    
                    // Add tool result to conversation history
                    addMessageToHistory(playerName, "tool", "Tool " + toolCall.getName() + " executed: " + resultText);
                } else {
                    toolResults.append("Failed to execute tool: ").append(toolCall.getName()).append("\n");
                }
                
            } catch (Exception e) {
                logger.warning("Tool execution error for " + toolCall.getName() + ": " + e.getMessage());
                toolResults.append("Error executing tool ").append(toolCall.getName()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        
        return toolResults.toString().trim();
    }
    
    /**
     * Format tool execution result
     */
    private String formatToolResult(String toolName, JsonObject result) {
        if (result.has("content")) {
            return "Tool " + toolName + " result: " + result.get("content").getAsString() + "\n";
        } else if (result.has("result")) {
            return "Tool " + toolName + " result: " + result.get("result").getAsString() + "\n";
        } else {
            return "Tool " + toolName + " executed successfully\n";
        }
    }
    
    /**
     * Clean message by removing trigger prefix
     */
    private String cleanMessage(String message) {
        String prefix = configuration.getTriggerPrefix();
        if (message.toLowerCase().startsWith(prefix.toLowerCase())) {
            return message.substring(prefix.length()).trim();
        }
        return message;
    }
    
    /**
     * Format AI response for display
     */
    private String formatResponse(String response) {
        return configuration.getResponseFormat().replace("%message%", response);
    }
    
    /**
     * Add message to conversation history
     */
    private void addMessageToHistory(String playerName, String role, String content) {
        ChatMessage message = new ChatMessage(role, content);
        
        // Add to global history
        globalMessageHistory.addMessage(message);
        
        // Add to player-specific history
        MessageHistory playerHistory = playerMessageHistories.computeIfAbsent(playerName, 
            k -> {
                MessageHistory hist = new MessageHistory();
                hist.setMaxHistoryLength(configuration.getMaxContextLength());
                return hist;
            });
        playerHistory.addMessage(message);
    }
    
    /**
     * Get conversation history for a player
     */
    private List<ChatMessage> getConversationHistory(String playerName) {
        if (configuration.isMonitorAllChat()) {
            return globalMessageHistory.getMessages();
        } else {
            MessageHistory playerHistory = playerMessageHistories.get(playerName);
            return playerHistory != null ? playerHistory.getMessages() : globalMessageHistory.getMessages();
        }
    }
    
    /**
     * Get recent messages for context
     */
    private List<ChatMessage> getRecentMessages(String playerName, int count) {
        List<ChatMessage> history = getConversationHistory(playerName);
        int size = history.size();
        int startIndex = Math.max(0, size - count);
        return history.subList(startIndex, size);
    }
    
    /**
     * Clear conversation history
     */
    public void clearHistory(String playerName) {
        if (playerName == null) {
            globalMessageHistory.clearHistory();
            playerMessageHistories.clear();
            logger.info("Cleared all conversation history");
        } else {
            MessageHistory playerHistory = playerMessageHistories.get(playerName);
            if (playerHistory != null) {
                playerHistory.clearHistory();
                logger.info("Cleared conversation history for player: " + playerName);
            }
        }
    }
    
    /**
     * Reload configuration and reinitialize providers
     */
    public void reloadConfiguration() {
        logger.info("Reloading AI configuration...");
        
        // Shutdown current providers
        if (primaryProvider != null) {
            primaryProvider.shutdown();
        }
        if (detectionProvider != null && detectionProvider != primaryProvider) {
            detectionProvider.shutdown();
        }
        
        // Clear caches
        providerFactory.clearCache();
        toolConverterFactory.clearCache();
        
        // Reload configuration
        plugin.reloadConfig();
        this.configuration = new AIConfiguration(plugin.getConfig(), logger);
        
        // Reinitialize providers
        initializeProviders();
        initializeMCPIntegration();
        initializeResponseDetection();
        
        logger.info("AI configuration reloaded successfully");
    }
    
    /**
     * Run startup test to verify AI functionality
     */
    public void runStartupTest() {
        CompletableFuture.runAsync(() -> {
            try {
                // Wait for server to be ready
                Thread.sleep(configuration.getMaxContextLength() > 0 ? 
                    Math.min(configuration.getMaxContextLength() * 100, 5000) : 5000);
                
                logger.info("Running AI startup test...");
                
                // Test primary provider
                CompletableFuture<String> testResponse = generateResponse("Hello! Please introduce yourself and report your current capabilities.", "Server");
                
                String response = testResponse.get();
                if (response != null && !response.trim().isEmpty()) {
                    // Broadcast to players if configured
                    boolean broadcastToPlayers = plugin.getConfig().getBoolean("startup-test.broadcast-to-players", true);
                    
                    if (broadcastToPlayers) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                player.sendMessage(response);
                            }
                        });
                    }
                    
                    logger.info("AI startup test completed successfully");
                } else {
                    logger.warning("AI startup test failed - no response generated");
                }
                
            } catch (Exception e) {
                logger.severe("AI startup test failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get current configuration summary
     */
    public String getConfigurationSummary() {
        return configuration.getConfigurationSummary();
    }
    
    /**
     * Get provider statistics
     */
    public String getProviderStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Provider Statistics:\n");
        stats.append("- Primary Provider: ").append(primaryProvider.getProviderName()).append("\n");
        stats.append("- Detection Provider: ").append(detectionProvider.getProviderName()).append("\n");
        stats.append("- Tools Enabled: ").append(toolsEnabled).append("\n");
        if (toolsEnabled && availableTools != null) {
            stats.append("- Available Tools: ").append(availableTools.size()).append("\n");
        }
        stats.append("- Active Player Histories: ").append(playerMessageHistories.size()).append("\n");
        stats.append("- Global History Size: ").append(globalMessageHistory.getMessages().size()).append("\n");
        return stats.toString();
    }
    
    /**
     * Change provider at runtime
     */
    public boolean changeProvider(String newProvider) {
        try {
            if (!AIProviderFactory.isProviderSupported(newProvider)) {
                return false;
            }
            
            Map<String, Object> providerConfig = configuration.getProviderConfiguration(newProvider);
            if (providerConfig == null) {
                return false;
            }
            
            // Shutdown current provider
            if (primaryProvider != null) {
                primaryProvider.shutdown();
            }
            
            // Create new provider
            AIProvider newPrimaryProvider = providerFactory.createProvider(newProvider, providerConfig);
            
            // Test connection
            Boolean connectionTest = newPrimaryProvider.testConnection().get();
            if (connectionTest) {
                this.primaryProvider = newPrimaryProvider;
                configuration.setPrimaryProvider(newProvider);
                logger.info("Successfully changed provider to: " + newProvider);
                return true;
            } else {
                logger.warning("Failed to connect to new provider: " + newProvider);
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("Error changing provider to " + newProvider + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Shutdown the AI service
     */
    public void shutdown() {
        logger.info("Shutting down AI service...");
        
        // Shutdown providers
        if (primaryProvider != null) {
            primaryProvider.shutdown();
        }
        if (detectionProvider != null && detectionProvider != primaryProvider) {
            detectionProvider.shutdown();
        }
        
        // Shutdown factories
        if (providerFactory != null) {
            providerFactory.shutdown();
        }
        
        // Shutdown MCP service
        if (mcpService != null) {
            mcpService.stopMCPBridge().join();
        }
        
        // Clear histories
        globalMessageHistory.clearHistory();
        playerMessageHistories.clear();
        
        logger.info("AI service shutdown complete");
    }
    
    // Getters for external access
    public AIConfiguration getConfiguration() { return configuration; }
    public boolean isToolsEnabled() { return toolsEnabled; }
    public List<JsonObject> getAvailableTools() { return availableTools; }
    public MessageHistory getGlobalMessageHistory() { return globalMessageHistory; }
    public Map<String, MessageHistory> getPlayerMessageHistories() { return playerMessageHistories; }
}