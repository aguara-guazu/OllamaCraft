package com.ollamacraft.plugin;

import com.google.gson.JsonObject;
import com.ollamacraft.plugin.config.AIConfiguration;
import com.ollamacraft.plugin.model.ChatMessage;
import com.ollamacraft.plugin.model.MessageHistory;
import com.ollamacraft.plugin.provider.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
    private List<JsonObject> originalMCPTools; // Store original MCP tools
    private Map<String, List<JsonObject>> convertedToolsCache; // Cache converted tools per provider
    private boolean toolsEnabled;
    
    public AIServiceV2(OllamaCraft plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.playerMessageHistories = new ConcurrentHashMap<>();
        this.convertedToolsCache = new ConcurrentHashMap<>();
        
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
            this.responseDetection = new ResponseDetectionService(configuration, providerFactory, logger);
            logger.info("Intelligent response detection initialized");
        } else {
            logger.info("Intelligent response detection disabled - using simple prefix detection");
        }
    }
    
    /**
     * Load available tools from MCP in original format
     */
    private void loadAvailableTools() {
        if (toolProvider != null) {
            try {
                this.originalMCPTools = toolProvider.getOriginalMCPTools().get();
                logger.info("Loaded " + originalMCPTools.size() + " original MCP tools");
                // Clear the converted tools cache when tools are reloaded
                convertedToolsCache.clear();
            } catch (Exception e) {
                logger.warning("Failed to load MCP tools: " + e.getMessage());
                this.originalMCPTools = new java.util.ArrayList<>();
            }
        }
    }
    
    /**
     * Get tools converted for a specific provider with caching
     * @param providerName Name of the provider
     * @return List of tools in provider-specific format
     */
    private List<JsonObject> getToolsForProvider(String providerName) {
        if (originalMCPTools == null || originalMCPTools.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Check cache first
        List<JsonObject> cached = convertedToolsCache.get(providerName);
        if (cached != null) {
            logger.fine("Using cached tools for provider: " + providerName);
            return cached;
        }
        
        // Convert tools for this provider
        try {
            ToolConverter converter = toolConverterFactory.getConverter(providerName);
            List<JsonObject> converted = converter.convertTools(originalMCPTools);
            
            // Validate converted tools
            List<JsonObject> validatedTools = new ArrayList<>();
            for (JsonObject tool : converted) {
                if (converter.validateTool(tool)) {
                    validatedTools.add(tool);
                } else {
                    logger.warning("Invalid tool format for provider " + providerName + ": " + tool.toString());
                }
            }
            
            // Cache the validated tools
            convertedToolsCache.put(providerName, validatedTools);
            logger.info("Converted and validated " + validatedTools.size() + "/" + converted.size() + " tools for provider: " + providerName);
            
            return validatedTools;
            
        } catch (Exception e) {
            logger.warning("Failed to convert tools for provider " + providerName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Process chat message and determine if AI should respond
     * @param player Player who sent the message
     * @param message Chat message
     * @return CompletableFuture<Boolean> indicating whether to respond
     */
    public CompletableFuture<Boolean> shouldRespond(Player player, String message) {
        // Quick prefix check for immediate response
        if (message.toLowerCase().startsWith(configuration.getTriggerPrefix().toLowerCase())) {
            return CompletableFuture.completedFuture(true);
        }
        
        // Use intelligent detection if enabled
        if (responseDetection != null) {
            return responseDetection.analyzeMessage(player, message)
                .thenApply(result -> result.shouldRespond() && 
                          result.getConfidence() >= configuration.getConfidenceThreshold());
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
                if (toolsEnabled && originalMCPTools != null && !originalMCPTools.isEmpty() && primaryProvider.supportsNativeTools()) {
                    // Get provider-specific tools
                    List<JsonObject> providerTools = getToolsForProvider(primaryProvider.getProviderName());
                    responseFuture = primaryProvider.chatWithTools(conversationHistory, configuration.getSystemPrompt(), providerTools);
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
     * Send a chat message and get AI response
     * @param player The player sending the message
     * @param content The message content
     * @return AI response
     */
    public String sendChatMessage(Player player, String content) {
        try {
            String playerName = player.getName();
            
            // Get or create player-specific message history
            MessageHistory playerHistory = playerMessageHistories.computeIfAbsent(playerName, 
                k -> {
                    MessageHistory history = new MessageHistory();
                    history.setMaxHistoryLength(configuration.getMaxContextLength());
                    return history;
                });
            
            // Add user message to history
            playerHistory.addMessage(new ChatMessage("user", playerName + ": " + content));
            
            // Process with primary provider
            if (primaryProvider == null) {
                return "I'm sorry, but I'm currently experiencing technical difficulties with my AI services. Please try again later.";
            }
            
            // Prepare messages for AI
            List<ChatMessage> messages = new ArrayList<>(playerHistory.getMessages());
            
            // Get AI response
            CompletableFuture<AIResponse> future;
            if (toolsEnabled && originalMCPTools != null && !originalMCPTools.isEmpty()) {
                List<JsonObject> providerTools = getToolsForProvider(primaryProvider.getProviderName());
                future = primaryProvider.chatWithTools(messages, configuration.getSystemPrompt(), providerTools);
            } else {
                future = primaryProvider.chat(messages, configuration.getSystemPrompt());
            }
            
            // Wait for response with timeout
            AIResponse response = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
            if (response.isSuccessful() && response.hasContent()) {
                String aiResponse = response.getContent();
                
                // Add AI response to history
                playerHistory.addMessage(new ChatMessage("assistant", aiResponse));
                
                return aiResponse;
            } else {
                logger.warning("AI response failed: " + response.getErrorMessage());
                return "I'm sorry, but I couldn't process your request right now. Please try again.";
            }
            
        } catch (Exception e) {
            logger.warning("Error processing chat message: " + e.getMessage());
            return "I'm experiencing technical difficulties. Please try again later.";
        }
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
     * Clear all conversation history (legacy compatibility)
     */
    public void clearHistory() {
        clearHistory(null);
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
        if (toolsEnabled && originalMCPTools != null) {
            stats.append("- Available Tools: ").append(originalMCPTools.size()).append("\n");
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
    
    /**
     * Perform startup integration test with configured provider
     */
    public void performStartupTest() {
        logger.info("Starting AI startup integration test...");
        logger.info("Testing configured provider: " + configuration.getProvider() + 
                   " with intelligent detection and multi-provider architecture");
        
        // Run async to avoid blocking server startup
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Build provider-agnostic startup test message
                StringBuilder testPrompt = new StringBuilder();
                testPrompt.append("Good morning! The server has just started and I'm ").append(configuration.getAgentName()).append(". ");
                testPrompt.append("Please introduce yourself to the players and report on your current capabilities. ");
                
                // Check MCP tools availability
                if (toolsEnabled && originalMCPTools != null && !originalMCPTools.isEmpty()) {
                    testPrompt.append("I have access to ").append(originalMCPTools.size()).append(" MCP tools for server management. ");
                } else {
                    testPrompt.append("Note that MCP tools are not currently available, so I'm operating in basic chat mode only. ");
                }
                
                // Add provider information
                testPrompt.append("I'm powered by ").append(configuration.getProvider()).append(" (")
                         .append(getCurrentProviderModel()).append("). ");
                testPrompt.append("Keep your response friendly, brief, and informative for the players. ");
                testPrompt.append("Maximum 2 sentences please.");
                
                // Process startup test with primary provider
                String response = processProviderStartupTest(testPrompt.toString());
                
                if (response != null && !response.isEmpty()) {
                    // Format and broadcast the response
                    String formattedResponse = formatStartupResponse(response);
                    broadcastStartupMessage(formattedResponse);
                    
                    logger.info("✓ AI startup integration test completed successfully");
                    logger.info("AI services are operational with " + configuration.getProvider() + " provider");
                } else {
                    logger.warning("✗ AI startup test failed - no response received from " + configuration.getProvider());
                    broadcastFallbackStartupMessage();
                }
                
            } catch (Exception e) {
                logger.warning("✗ AI startup integration test failed: " + e.getMessage());
                logger.warning("This usually indicates provider configuration issues");
                logger.warning("Check: 1) Provider is accessible, 2) API keys are valid, 3) Model is available");
                
                broadcastFallbackStartupMessage();
            }
        });
    }
    
    /**
     * Process startup test with the configured provider
     */
    private String processProviderStartupTest(String testPrompt) {
        try {
            if (primaryProvider == null) {
                logger.warning("Primary provider not available for startup test");
                return null;
            }
            
            // Create startup test message
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("user", testPrompt));
            
            // Test provider connection and get response
            CompletableFuture<AIResponse> future;
            if (toolsEnabled && originalMCPTools != null && !originalMCPTools.isEmpty()) {
                List<JsonObject> providerTools = getToolsForProvider(primaryProvider.getProviderName());
                logger.info("Testing with tools enabled (" + providerTools.size() + " tools available)");
                future = primaryProvider.chatWithTools(messages, configuration.getSystemPrompt(), providerTools);
            } else {
                logger.info("Testing basic chat without tools");
                future = primaryProvider.chat(messages, configuration.getSystemPrompt());
            }
            
            // Wait for response with timeout
            AIResponse response = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
            if (response.isSuccessful()) {
                // Handle responses with tool calls
                if (response.hasToolCalls()) {
                    logger.info("Startup test successful with " + configuration.getProvider() + " (with " + response.getToolCalls().size() + " tool calls)");
                    
                    // Process tool calls and return the result
                    String processedResponse = processToolCallsForStartup(response.getToolCalls(), response.getContent());
                    return processedResponse != null ? processedResponse : "Startup test completed with tool execution.";
                }
                // Handle normal text responses
                else if (response.hasContent()) {
                    logger.info("Startup test successful with " + configuration.getProvider());
                    return response.getContent();
                }
                // Success but no content or tool calls
                else {
                    logger.info("Startup test successful with " + configuration.getProvider() + " (empty response)");
                    return "Startup test completed successfully.";
                }
            } else {
                logger.warning("Startup test failed: " + response.getErrorMessage());
                return null;
            }
            
        } catch (Exception e) {
            logger.warning("Provider startup test failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Process tool calls for startup test
     * @param toolCalls List of tool calls to process
     * @param initialContent Initial response content
     * @return Processed response content
     */
    private String processToolCallsForStartup(List<ToolCall> toolCalls, String initialContent) {
        if (toolExecutor == null || toolCalls.isEmpty()) {
            return initialContent;
        }
        
        StringBuilder result = new StringBuilder();
        if (initialContent != null && !initialContent.trim().isEmpty()) {
            result.append(initialContent).append(" ");
        }
        
        // Execute tool calls and collect results
        int successfulCalls = 0;
        for (ToolCall toolCall : toolCalls) {
            try {
                logger.info("Executing startup tool call: " + toolCall.getName());
                
                // Convert tool call to MCP format
                JsonObject mcpToolCall = toolCall.toMCPExecutorFormat();
                
                // Execute tool
                JsonObject toolResult = toolExecutor.executeToolCall(mcpToolCall).get(10, java.util.concurrent.TimeUnit.SECONDS);
                
                if (toolResult != null) {
                    successfulCalls++;
                    logger.info("Tool " + toolCall.getName() + " executed successfully during startup");
                } else {
                    logger.warning("Tool " + toolCall.getName() + " failed during startup");
                }
                
            } catch (Exception e) {
                logger.warning("Startup tool execution failed for " + toolCall.getName() + ": " + e.getMessage());
            }
        }
        
        result.append("Executed ").append(successfulCalls).append("/").append(toolCalls.size())
              .append(" tools successfully during startup test.");
        
        return result.toString();
    }
    
    /**
     * Get current provider model name
     */
    private String getCurrentProviderModel() {
        try {
            Map<String, Object> providerConfig = configuration.getProviderConfiguration(configuration.getProvider());
            return (String) providerConfig.getOrDefault("model", "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Format startup response for display
     */
    private String formatStartupResponse(String response) {
        String agentName = configuration.getAgentName();
        return "[" + agentName + "] " + response;
    }
    
    /**
     * Broadcast startup message to all players
     */
    private void broadcastStartupMessage(String formattedResponse) {
        // Check if broadcasting is enabled
        boolean broadcastEnabled = plugin.getConfig().getBoolean("startup-test.broadcast-to-players", true);
        
        if (broadcastEnabled) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(formattedResponse)
                        .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
            });
        }
        
        logger.info("AI Startup Message: " + formattedResponse);
    }
    
    /**
     * Broadcast fallback message when startup test fails
     */
    private void broadcastFallbackStartupMessage() {
        String agentName = configuration.getAgentName();
        String fallbackMessage = "[" + agentName + "] Hello! The server has started, but I'm currently experiencing technical difficulties connecting to my AI services. Basic server functionality is working normally.";
        
        broadcastStartupMessage(fallbackMessage);
    }
    
    // Getters for external access
    public AIConfiguration getConfiguration() { return configuration; }
    public boolean isToolsEnabled() { return toolsEnabled; }
    public List<JsonObject> getAvailableTools() { return originalMCPTools; }
    public MessageHistory getGlobalMessageHistory() { return globalMessageHistory; }
    public Map<String, MessageHistory> getPlayerMessageHistories() { return playerMessageHistories; }
}