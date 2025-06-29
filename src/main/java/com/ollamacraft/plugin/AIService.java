package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ollamacraft.plugin.model.ChatMessage;
import com.ollamacraft.plugin.model.MessageHistory;
import okhttp3.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Service to interact with Ollama AI API
 */
public class AIService {
    
    private final OllamaCraft plugin;
    private final OkHttpClient client;
    private final Gson gson;
    private final MessageHistory messageHistory;
    private final MCPService mcpService;
    
    private String apiUrl;
    private String model;
    private double temperature;
    private String systemPrompt;
    private int maxContextLength;
    private boolean mcpToolsEnabled;
    
    // MCP tool integration
    private MCPToolProvider toolProvider;
    private MCPToolExecutor toolExecutor;
    
    /**
     * Constructor for AIService
     * @param plugin The OllamaCraft plugin instance
     */
    public AIService(OllamaCraft plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.messageHistory = new MessageHistory();
        this.mcpService = new MCPService(plugin);
        
        // Initialize HTTP client
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        
        // Load configuration
        loadConfig();
    }
    
    /**
     * Load configuration from config.yml
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        this.apiUrl = config.getString("ollama.api-url", "http://localhost:11434/api");
        this.model = config.getString("ollama.model", "llama3");
        this.temperature = config.getDouble("ollama.temperature", 0.7);
        this.systemPrompt = config.getString("ollama.system-prompt", 
                "You are Steve, a helpful assistant in a Minecraft world. You have access to Minecraft tools through MCP that allow you to execute commands and interact with the server.");
        this.maxContextLength = config.getInt("ollama.max-context-length", 50);
        this.mcpToolsEnabled = config.getBoolean("mcp.tools.enabled", true);
        
        // Update message history max length
        messageHistory.setMaxHistoryLength(maxContextLength);
        
        // Initialize MCP service
        mcpService.initialize();
        
        // Initialize MCP tools if enabled and MCP service is running
        initializeMCPTools();
    }
    
    /**
     * Initialize MCP tool integration
     */
    private void initializeMCPTools() {
        if (!mcpToolsEnabled || !mcpService.isEnabled()) {
            plugin.getLogger().info("MCP tools integration disabled - AI will work without tool capabilities");
            toolProvider = null;
            toolExecutor = null;
            return;
        }
        
        try {
            // Wait a moment for MCP service to be ready
            Thread.sleep(1000);
            
            if (mcpService.isRunning()) {
                // Create HTTP client for tool communication
                MCPHttpClient.MCPHttpClientOptions options = new MCPHttpClient.MCPHttpClientOptions(
                    mcpService.getServerUrl(), 
                    mcpService.getApiKey()
                )
                .debug(false)
                .logger(plugin.getLogger());
                
                MCPHttpClient httpClient = new MCPHttpClient(options);
                
                toolProvider = new MCPToolProvider(httpClient, plugin.getLogger());
                toolExecutor = new MCPToolExecutor(httpClient, toolProvider, plugin.getLogger());
                
                plugin.getLogger().info("MCP tools integration initialized with native Java bridge");
            } else {
                plugin.getLogger().info("MCP bridge not running - AI will work with basic chat functionality only");
                toolProvider = null;
                toolExecutor = null;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize MCP tools integration - AI will work with basic chat functionality only", e);
            toolProvider = null;
            toolExecutor = null;
        }
    }
    
    /**
     * Send a chat message to the AI and get a response
     * @param player The player sending the message
     * @param content The message content
     * @return The AI's response
     */
    public String sendChatMessage(Player player, String content) {
        try {
            // Add user message to history
            messageHistory.addMessage(new ChatMessage("user", player.getName() + ": " + content));
            
            // Process the conversation with potential tool calls
            return processConversationWithTools(player);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error sending chat message to AI", e);
            return "Sorry, I encountered an error processing your request.";
        }
    }
    
    /**
     * Process conversation with potential tool calls
     * @param player The player for context
     * @return The final AI response
     */
    private String processConversationWithTools(Player player) throws Exception {
        int maxToolCalls = 5; // Prevent infinite loops
        int toolCallCount = 0;
        
        while (toolCallCount < maxToolCalls) {
            // Create request body
            JsonObject requestBody = buildChatRequest();
            
            // Add tools if available
            if (toolProvider != null && mcpToolsEnabled) {
                List<JsonObject> tools = toolProvider.getToolsForOllama().join();
                if (!tools.isEmpty()) {
                    JsonArray toolsArray = new JsonArray();
                    for (JsonObject tool : tools) {
                        toolsArray.add(tool);
                    }
                    requestBody.add("tools", toolsArray);
                }
            }
            
            // Build and execute HTTP request
            Request request = new Request.Builder()
                .url(apiUrl + "/chat")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
            
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }
            
            // Parse response
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("message")) {
                throw new IOException("Invalid response format: missing message");
            }
            
            JsonObject message = jsonResponse.getAsJsonObject("message");
            
            // Check if the AI wants to call tools
            if (message.has("tool_calls")) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                toolCallCount++;
                
                plugin.getLogger().info("AI requested " + toolCalls.size() + " tool calls");
                
                // Add the assistant message with tool calls to history
                String assistantContent = message.has("content") ? 
                    message.get("content").getAsString() : "";
                messageHistory.addMessage(new ChatMessage("assistant", assistantContent));
                
                // Execute each tool call
                for (JsonElement toolCallElement : toolCalls) {
                    JsonObject toolCall = toolCallElement.getAsJsonObject();
                    
                    if (toolExecutor != null && toolExecutor.isValidToolCall(toolCall)) {
                        JsonObject toolResult = toolExecutor.executeToolCall(toolCall).join();
                        
                        // Add tool result to history
                        String toolResultContent = toolResult.has("content") ? 
                            toolResult.get("content").getAsString() : "Tool executed";
                        messageHistory.addMessage(new ChatMessage("tool", toolResultContent));
                        
                        plugin.getLogger().info("Tool executed: " + toolResultContent);
                    } else {
                        // Add error result for invalid tool call
                        messageHistory.addMessage(new ChatMessage("tool", "Error: Invalid tool call"));
                    }
                }
                
                // Continue the loop to get the AI's response after tool execution
                continue;
            }
            
            // No tool calls, this is the final response
            String aiResponse = message.has("content") ? 
                message.get("content").getAsString() : "";
            
            // Add assistant response to history
            messageHistory.addMessage(new ChatMessage("assistant", aiResponse));
            
            return aiResponse;
        }
        
        // Too many tool calls, return a message
        String fallbackResponse = "I've made several tool calls but let me summarize what I found.";
        messageHistory.addMessage(new ChatMessage("assistant", fallbackResponse));
        return fallbackResponse;
    }
    
    /**
     * Build the chat request JSON
     * @return The request body
     */
    private JsonObject buildChatRequest() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("stream", false);
        
        // Add messages history
        JsonArray messages = new JsonArray();
        
        // Add system message if configured
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
        }
        
        // Add chat history
        for (ChatMessage message : messageHistory.getMessages()) {
            JsonObject chatMessage = new JsonObject();
            chatMessage.addProperty("role", message.getRole());
            chatMessage.addProperty("content", message.getContent());
            messages.add(chatMessage);
        }
        
        requestBody.add("messages", messages);
        return requestBody;
    }
    
    /**
     * Clear the message history for all users
     */
    public void clearHistory() {
        messageHistory.clearHistory();
    }
    
    /**
     * Clean up resources when the service is shutting down
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
    
    /**
     * Get the current AI model
     * @return The model name
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Set the AI model
     * @param model The model name
     */
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * Get the current temperature setting
     * @return The temperature value
     */
    public double getTemperature() {
        return temperature;
    }
    
    /**
     * Set the temperature value
     * @param temperature The temperature value
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    /**
     * Get the current system prompt
     * @return The system prompt
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    /**
     * Set the system prompt
     * @param systemPrompt The system prompt
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    /**
     * Get the MCP service
     * @return The MCP service instance
     */
    public MCPService getMcpService() {
        return mcpService;
    }
    
    /**
     * Perform startup integration test to verify AI and MCP functionality
     * This method sends a greeting message and reports available tools
     */
    public void performStartupTest() {
        plugin.getLogger().info("Starting AI integration test...");
        
        // Run async to avoid blocking server startup
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Build startup test message
                StringBuilder testPrompt = new StringBuilder();
                testPrompt.append("Good morning! The server has just started. ");
                testPrompt.append("Please introduce yourself to the players and report on your current capabilities. ");
                
                // Check MCP tools availability
                if (toolProvider != null && mcpService.isRunning()) {
                    testPrompt.append("Please list the MCP tools you have access to and briefly explain what you can do with them. ");
                } else {
                    testPrompt.append("Note that MCP tools are not currently available, so you're operating in basic chat mode only. ");
                }
                
                testPrompt.append("Keep your response friendly and informative for the players.");
                
                // Add startup message to history
                messageHistory.addMessage(new ChatMessage("user", "SYSTEM_STARTUP_TEST: " + testPrompt.toString()));
                
                // Process the conversation to get AI response
                String response = processStartupTest();
                
                if (response != null && !response.isEmpty()) {
                    // Format and broadcast the response
                    String formattedResponse = formatStartupResponse(response);
                    broadcastStartupMessage(formattedResponse);
                    
                    plugin.getLogger().info("AI startup test completed successfully");
                } else {
                    plugin.getLogger().warning("AI startup test failed - no response received");
                }
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "AI startup test failed", e);
            }
        });
    }
    
    /**
     * Process the startup test conversation
     * @return The AI's response to the startup test
     */
    private String processStartupTest() throws Exception {
        int maxToolCalls = 3; // Limit tool calls during startup test
        int toolCallCount = 0;
        
        while (toolCallCount < maxToolCalls) {
            // Create request body
            JsonObject requestBody = buildChatRequest();
            
            // Add tools if available
            if (toolProvider != null && mcpToolsEnabled && mcpService.isRunning()) {
                List<JsonObject> tools = toolProvider.getToolsForOllama().join();
                if (!tools.isEmpty()) {
                    JsonArray toolsArray = new JsonArray();
                    for (JsonObject tool : tools) {
                        toolsArray.add(tool);
                    }
                    requestBody.add("tools", toolsArray);
                    plugin.getLogger().info("Startup test: " + tools.size() + " MCP tools available to AI");
                }
            }
            
            // Build and execute HTTP request
            Request request = new Request.Builder()
                .url(apiUrl + "/chat")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
            
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }
            
            // Parse response
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("message")) {
                throw new IOException("Invalid response format: missing message");
            }
            
            JsonObject message = jsonResponse.getAsJsonObject("message");
            
            // Check if the AI wants to call tools
            if (message.has("tool_calls")) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                toolCallCount++;
                
                plugin.getLogger().info("Startup test: AI requested " + toolCalls.size() + " tool calls");
                
                // Add the assistant message with tool calls to history
                String assistantContent = message.has("content") ? 
                    message.get("content").getAsString() : "";
                messageHistory.addMessage(new ChatMessage("assistant", assistantContent));
                
                // Execute each tool call
                for (JsonElement toolCallElement : toolCalls) {
                    JsonObject toolCall = toolCallElement.getAsJsonObject();
                    
                    if (toolExecutor != null && toolExecutor.isValidToolCall(toolCall)) {
                        JsonObject toolResult = toolExecutor.executeToolCall(toolCall).join();
                        
                        // Add tool result to history
                        String toolResultContent = toolResult.has("content") ? 
                            toolResult.get("content").getAsString() : "Tool executed";
                        messageHistory.addMessage(new ChatMessage("tool", toolResultContent));
                        
                        plugin.getLogger().info("Startup test: Tool executed successfully");
                    } else {
                        // Add error result for invalid tool call
                        messageHistory.addMessage(new ChatMessage("tool", "Error: Invalid tool call"));
                    }
                }
                
                // Continue the loop to get the AI's final response
                continue;
            }
            
            // No tool calls, this is the final response
            String aiResponse = message.has("content") ? 
                message.get("content").getAsString() : "";
            
            // Add assistant response to history
            messageHistory.addMessage(new ChatMessage("assistant", aiResponse));
            
            return aiResponse;
        }
        
        // Too many tool calls, return a fallback message
        return "Hello! The server has started and I'm ready to assist you. My systems are operational!";
    }
    
    /**
     * Format the startup response for display
     * @param response The raw AI response
     * @return Formatted response
     */
    private String formatStartupResponse(String response) {
        FileConfiguration config = plugin.getConfig();
        String format = config.getString("chat.response-format", "[Steve] &a%message%");
        return format.replace("%message%", response);
    }
    
    /**
     * Broadcast the startup message to players and console
     * @param message The formatted message to broadcast
     */
    private void broadcastStartupMessage(String message) {
        FileConfiguration config = plugin.getConfig();
        boolean broadcastToPlayers = config.getBoolean("startup-test.broadcast-to-players", true);
        
        // Always log to console
        plugin.getLogger().info("AI Startup Message: " + message);
        
        // Broadcast to players if enabled
        if (broadcastToPlayers) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().broadcastMessage(message);
            });
        }
    }
}