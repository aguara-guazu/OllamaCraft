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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to interact with Ollama AI API
 */
public class AIService {
    
    private final OllamaCraft plugin;
    private OkHttpClient client;
    private final Gson gson;
    private final MessageHistory messageHistory;
    private final MCPService mcpService;
    
    private String apiUrl;
    private String model;
    private double temperature;
    private String systemPrompt;
    private int maxContextLength;
    private boolean mcpToolsEnabled;
    
    // Error handling configuration
    private int maxRetries;
    private int baseRetryDelayMs;
    private int requestTimeoutSeconds;
    
    // Fallback tool calling for models without native tool support
    private boolean useToolFallbackMode = false;
    
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
        
        // Initialize HTTP client - will be reconfigured in loadConfig()
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
        
        // Load error handling configuration
        this.maxRetries = config.getInt("ollama.error-handling.max-retries", 3);
        this.baseRetryDelayMs = config.getInt("ollama.error-handling.base-retry-delay-ms", 1000);
        this.requestTimeoutSeconds = config.getInt("ollama.error-handling.request-timeout-seconds", 60);
        
        // Recreate HTTP client with new timeout configuration
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(this.requestTimeoutSeconds, TimeUnit.SECONDS)
            .build();
        
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
            plugin.getLogger().log(Level.WARNING, "Error sending chat message to AI: " + e.getMessage(), e);
            return "I'm sorry, but I'm currently experiencing technical difficulties. Please try again in a moment, or contact an administrator if the problem persists.";
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
            
            // Build and execute HTTP request with retry logic
            String responseBody = executeOllamaRequestWithRetry(requestBody);
            if (responseBody == null) {
                // Request failed after retries, return fallback response
                return "I'm sorry, but I'm currently experiencing technical difficulties connecting to my AI services. Please try again later.";
            } else if ("TOOL_FALLBACK_MODE_REQUIRED".equals(responseBody)) {
                // Model doesn't support native tool calling, use fallback mode
                plugin.getLogger().info("Attempting conversation with fallback tool calling mode");
                return processConversationWithFallbackTools(player);
            }
            
            // Parse response
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("message")) {
                plugin.getLogger().warning("Invalid Ollama API response format: missing message field");
                return "I received an invalid response from my AI services. Please try again.";
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
     * Process conversation using fallback tool calling for models that don't support native tools
     * @param player The player for context
     * @return The final AI response
     */
    private String processConversationWithFallbackTools(Player player) throws Exception {
        plugin.getLogger().info("Using fallback tool calling mode for model that doesn't support native tools");
        
        int maxToolCalls = 5; // Prevent infinite loops
        int toolCallCount = 0;
        int parseRetries = 0;
        int maxParseRetries = 3;
        
        while (toolCallCount < maxToolCalls) {
            // Create request body with tool descriptions in system prompt
            JsonObject requestBody = buildChatRequestWithToolDescriptions();
            
            // Execute request without native tools
            String responseBody = executeOllamaRequestWithoutTools(requestBody);
            if (responseBody == null) {
                return "I'm sorry, but I'm currently experiencing technical difficulties connecting to my AI services. Please try again later.";
            }
            
            // Parse response
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("message")) {
                plugin.getLogger().warning("Invalid Ollama API response format: missing message field");
                return "I received an invalid response from my AI services. Please try again.";
            }
            
            JsonObject message = jsonResponse.getAsJsonObject("message");
            String aiResponse = message.has("content") ? message.get("content").getAsString() : "";
            
            // Try to parse tool calls from the text response
            ToolCallParseResult parseResult = parseTextBasedToolCalls(aiResponse);
            
            if (parseResult.hasToolCalls()) {
                toolCallCount++;
                plugin.getLogger().info("Parsed " + parseResult.getToolCalls().size() + " tool calls from AI response");
                
                // Add the assistant message to history
                messageHistory.addMessage(new ChatMessage("assistant", parseResult.getCleanedResponse()));
                
                // Execute each tool call
                boolean allToolsSuccessful = true;
                for (ParsedToolCall toolCall : parseResult.getToolCalls()) {
                    try {
                        // Convert to MCP format and execute
                        JsonObject mcpToolCall = convertToMCPToolCall(toolCall);
                        JsonObject toolResult = toolExecutor.executeToolCall(mcpToolCall).join();
                        
                        // Add tool result to history
                        String toolResultContent = toolResult.has("content") ? 
                            toolResult.get("content").getAsString() : "Tool executed successfully";
                        messageHistory.addMessage(new ChatMessage("tool", toolResultContent));
                        
                        plugin.getLogger().info("Fallback tool executed: " + toolCall.getToolName() + " -> " + toolResultContent);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to execute fallback tool " + toolCall.getToolName() + ": " + e.getMessage());
                        messageHistory.addMessage(new ChatMessage("tool", "Error: Tool execution failed - " + e.getMessage()));
                        allToolsSuccessful = false;
                    }
                }
                
                // Continue the loop to get the AI's response after tool execution
                parseRetries = 0; // Reset parse retries on successful tool execution
                continue;
                
            } else if (parseResult.hasMalformedToolCall()) {
                // Response seems to attempt tool calling but is malformed
                parseRetries++;
                plugin.getLogger().warning("Detected malformed tool call attempt (retry " + parseRetries + "/" + maxParseRetries + ")");
                
                if (parseRetries < maxParseRetries) {
                    // Add clarification message and retry
                    String clarificationPrompt = "I notice you tried to use a tool, but the format was incorrect. " +
                        "Please use the exact format: TOOL_CALL: tool_name\\nPARAMETERS: {\"param\": \"value\"}\\nREASONING: why you're using this tool";
                    messageHistory.addMessage(new ChatMessage("user", clarificationPrompt));
                    continue;
                } else {
                    // Too many failed parse attempts
                    plugin.getLogger().warning("Too many malformed tool call attempts, giving up on tool parsing");
                    String fallbackMessage = "I'm having trouble with my tool calling system. Let me provide a direct response instead: " + aiResponse;
                    messageHistory.addMessage(new ChatMessage("assistant", fallbackMessage));
                    return fallbackMessage;
                }
            }
            
            // No tool calls detected, this is the final response
            messageHistory.addMessage(new ChatMessage("assistant", aiResponse));
            return aiResponse;
        }
        
        // Too many tool calls, return a message
        String fallbackResponse = "I've attempted several tool operations but let me provide you with a summary of what I found.";
        messageHistory.addMessage(new ChatMessage("assistant", fallbackResponse));
        return fallbackResponse;
    }
    
    /**
     * Helper class to represent a parsed tool call from text
     */
    private static class ParsedToolCall {
        private final String toolName;
        private final JsonObject parameters;
        private final String reasoning;
        
        public ParsedToolCall(String toolName, JsonObject parameters, String reasoning) {
            this.toolName = toolName;
            this.parameters = parameters;
            this.reasoning = reasoning;
        }
        
        public String getToolName() { return toolName; }
        public JsonObject getParameters() { return parameters; }
        public String getReasoning() { return reasoning; }
    }
    
    /**
     * Helper class to represent the result of parsing tool calls from text
     */
    private static class ToolCallParseResult {
        private final List<ParsedToolCall> toolCalls;
        private final String cleanedResponse;
        private final boolean hasMalformedToolCall;
        
        public ToolCallParseResult(List<ParsedToolCall> toolCalls, String cleanedResponse, boolean hasMalformedToolCall) {
            this.toolCalls = toolCalls;
            this.cleanedResponse = cleanedResponse;
            this.hasMalformedToolCall = hasMalformedToolCall;
        }
        
        public boolean hasToolCalls() { return !toolCalls.isEmpty(); }
        public boolean hasMalformedToolCall() { return hasMalformedToolCall; }
        public List<ParsedToolCall> getToolCalls() { return toolCalls; }
        public String getCleanedResponse() { return cleanedResponse; }
    }
    
    /**
     * Execute Ollama API request with retry logic and error handling
     * @param requestBody The JSON request body
     * @return Response body string if successful, null if failed
     */
    private String executeOllamaRequestWithRetry(JsonObject requestBody) {
        plugin.getLogger().info("Starting Ollama API request to " + apiUrl + "/chat (model: " + model + ")");
        
        Exception lastException = null;
        int lastStatusCode = 0;
        String lastErrorMessage = "";
        
        for (int attempt = 1; attempt <= this.maxRetries; attempt++) {
            try {
                plugin.getLogger().info("Ollama API request attempt " + attempt + "/" + this.maxRetries);
                
                // Build HTTP request
                Request request = new Request.Builder()
                    .url(apiUrl + "/chat")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();
                
                // Execute request
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            plugin.getLogger().info("Ollama API request successful on attempt " + attempt);
                            return body.string();
                        } else {
                            lastErrorMessage = "Empty response body";
                            plugin.getLogger().warning("Ollama API returned empty response body (attempt " + attempt + "/" + this.maxRetries + ")");
                        }
                    } else {
                        lastStatusCode = response.code();
                        String statusMessage = response.message();
                        lastErrorMessage = categorizeOllamaError(response.code(), statusMessage);
                        
                        // Log detailed error information
                        plugin.getLogger().warning("Ollama API HTTP " + response.code() + " error (attempt " + attempt + "/" + this.maxRetries + "): " + statusMessage);
                        
                        // Try to get response body for additional error details
                        String errorContent = "";
                        try {
                            ResponseBody errorBody = response.body();
                            if (errorBody != null) {
                                errorContent = errorBody.string();
                                if (!errorContent.isEmpty()) {
                                    plugin.getLogger().warning("Ollama API error response body: " + errorContent);
                                }
                            }
                        } catch (Exception bodyException) {
                            plugin.getLogger().warning("Could not read error response body: " + bodyException.getMessage());
                        }
                        
                        // Check if model doesn't support tools and should use fallback mode
                        if (response.code() == 400 && errorContent.contains("does not support tools")) {
                            plugin.getLogger().warning("Model does not support native tool calling - switching to fallback mode");
                            this.useToolFallbackMode = true;
                            // Return special marker to indicate we should retry with fallback mode
                            return "TOOL_FALLBACK_MODE_REQUIRED";
                        }
                        
                        // Check for other non-recoverable errors but don't return immediately
                        if (response.code() == 401 || response.code() == 403 || response.code() == 404) {
                            plugin.getLogger().severe("Non-recoverable Ollama API error (HTTP " + response.code() + "): " + lastErrorMessage);
                            plugin.getLogger().severe("Stopping retry attempts due to non-recoverable error");
                            return null;
                        }
                    }
                }
                
            } catch (IOException e) {
                lastException = e;
                lastErrorMessage = categorizeOllamaIOException(e);
                plugin.getLogger().warning("Ollama API connection error (attempt " + attempt + "/" + this.maxRetries + "): " + e.getMessage());
                
                // Check for non-recoverable connection errors but don't return immediately
                if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                    plugin.getLogger().severe("Ollama server appears to be offline: " + lastErrorMessage);
                    plugin.getLogger().severe("Stopping retry attempts - Ollama server is not responding");
                    return null;
                }
            } catch (Exception e) {
                lastException = e;
                lastErrorMessage = "Unexpected error: " + e.getMessage();
                plugin.getLogger().log(Level.WARNING, "Unexpected error during Ollama API request (attempt " + attempt + "/" + this.maxRetries + ")", e);
            }
            
            // Wait before retrying (exponential backoff) - but not on the last attempt
            if (attempt < this.maxRetries) {
                int delay = this.baseRetryDelayMs * (int) Math.pow(2, attempt - 1);
                plugin.getLogger().info("Retrying Ollama request in " + delay + "ms... (attempt " + (attempt + 1) + "/" + this.maxRetries + ")");
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().warning("Retry delay interrupted - stopping retry attempts");
                    break;
                }
            }
        }
        
        // All retries failed - log detailed failure information
        plugin.getLogger().severe("Ollama API request failed after " + this.maxRetries + " attempts");
        if (lastStatusCode > 0) {
            plugin.getLogger().severe("Final error: HTTP " + lastStatusCode + " - " + lastErrorMessage);
        } else if (lastException != null) {
            plugin.getLogger().severe("Final error: " + lastErrorMessage);
            plugin.getLogger().log(Level.SEVERE, "Final exception details:", lastException);
        } else {
            plugin.getLogger().severe("Final error: " + lastErrorMessage);
        }
        return null;
    }
    
    /**
     * Categorize Ollama API HTTP errors into user-friendly messages
     * @param statusCode HTTP status code
     * @param statusMessage HTTP status message
     * @return User-friendly error message
     */
    private String categorizeOllamaError(int statusCode, String statusMessage) {
        switch (statusCode) {
            case 400:
                return "Bad request to Ollama API. Check model name and request format.";
            case 401:
                return "Unauthorized access to Ollama API. Check authentication.";
            case 404:
                return "Ollama API endpoint not found. Check the API URL configuration.";
            case 500:
                return "Ollama server internal error. The AI model may be unavailable or overloaded.";
            case 502:
                return "Ollama server gateway error. The service may be temporarily unavailable.";
            case 503:
                return "Ollama server unavailable. The service may be starting up or overloaded.";
            case 504:
                return "Ollama server timeout. The request took too long to process.";
            default:
                return "Ollama API error (" + statusCode + "): " + statusMessage;
        }
    }
    
    /**
     * Categorize Ollama IO exceptions into user-friendly messages
     * @param exception The IOException
     * @return User-friendly error message
     */
    private String categorizeOllamaIOException(IOException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "Unknown connection error to Ollama API";
        }
        
        if (message.contains("Connection refused")) {
            return "Cannot connect to Ollama server. Is Ollama running at " + apiUrl + "?";
        } else if (message.contains("timeout")) {
            return "Connection to Ollama server timed out. The server may be overloaded.";
        } else if (message.contains("Unknown host")) {
            return "Cannot resolve Ollama server hostname. Check the API URL configuration.";
        } else if (message.contains("No route to host")) {
            return "Cannot reach Ollama server. Check network connectivity.";
        } else {
            return "Connection error: " + message;
        }
    }
    
    /**
     * Execute Ollama API request without native tools for fallback mode
     * @param requestBody The JSON request body (without tools array)
     * @return Response body string if successful, null if failed
     */
    private String executeOllamaRequestWithoutTools(JsonObject requestBody) {
        plugin.getLogger().info("Starting fallback Ollama API request to " + apiUrl + "/chat (model: " + model + ")");
        
        for (int attempt = 1; attempt <= this.maxRetries; attempt++) {
            try {
                plugin.getLogger().info("Fallback Ollama API request attempt " + attempt + "/" + this.maxRetries);
                
                // Build HTTP request
                Request request = new Request.Builder()
                    .url(apiUrl + "/chat")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();
                
                // Execute request
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            plugin.getLogger().info("Fallback Ollama API request successful on attempt " + attempt);
                            return body.string();
                        } else {
                            plugin.getLogger().warning("Fallback Ollama API returned empty response body (attempt " + attempt + "/" + this.maxRetries + ")");
                        }
                    } else {
                        String statusMessage = response.message();
                        String errorMessage = categorizeOllamaError(response.code(), statusMessage);
                        plugin.getLogger().warning("Fallback Ollama API HTTP " + response.code() + " error (attempt " + attempt + "/" + this.maxRetries + "): " + statusMessage);
                        
                        // Non-recoverable errors
                        if (response.code() == 401 || response.code() == 403 || response.code() == 404) {
                            plugin.getLogger().severe("Non-recoverable Ollama API error: " + errorMessage);
                            return null;
                        }
                    }
                }
                
            } catch (IOException e) {
                String errorMessage = categorizeOllamaIOException(e);
                plugin.getLogger().warning("Fallback Ollama API connection error (attempt " + attempt + "/" + this.maxRetries + "): " + e.getMessage());
                
                if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                    plugin.getLogger().severe("Ollama server appears to be offline: " + errorMessage);
                    return null;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Unexpected error during fallback Ollama API request (attempt " + attempt + "/" + this.maxRetries + ")", e);
            }
            
            // Wait before retrying
            if (attempt < this.maxRetries) {
                int delay = this.baseRetryDelayMs * (int) Math.pow(2, attempt - 1);
                plugin.getLogger().info("Retrying fallback Ollama request in " + delay + "ms... (attempt " + (attempt + 1) + "/" + this.maxRetries + ")");
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().warning("Fallback retry delay interrupted - stopping retry attempts");
                    break;
                }
            }
        }
        
        plugin.getLogger().severe("Fallback Ollama API request failed after " + this.maxRetries + " attempts");
        return null;
    }
    
    /**
     * Build chat request with tool descriptions in system prompt for fallback mode
     * @return The request body with enhanced system prompt
     */
    private JsonObject buildChatRequestWithToolDescriptions() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("stream", false);
        
        // Add messages history with enhanced system prompt
        JsonArray messages = new JsonArray();
        
        // Create enhanced system message with tool descriptions
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            StringBuilder enhancedPrompt = new StringBuilder(systemPrompt);
            
            // Add tool descriptions if available
            if (toolProvider != null && mcpToolsEnabled) {
                try {
                    List<JsonObject> tools = toolProvider.getToolsForOllama().join();
                    if (!tools.isEmpty()) {
                        enhancedPrompt.append("\\n\\nAvailable Tools:\\n");
                        enhancedPrompt.append("When you need to use a tool, respond with the following format:\\n");
                        enhancedPrompt.append("TOOL_CALL: tool_name\\n");
                        enhancedPrompt.append("PARAMETERS: {\\\"param1\\\": \\\"value1\\\", \\\"param2\\\": \\\"value2\\\"}\\n");
                        enhancedPrompt.append("REASONING: Brief explanation of why you're using this tool\\n\\n");
                        
                        for (JsonObject tool : tools) {
                            if (tool.has("function")) {
                                JsonObject function = tool.getAsJsonObject("function");
                                String toolName = function.has("name") ? function.get("name").getAsString() : "unknown";
                                String description = function.has("description") ? function.get("description").getAsString() : "No description";
                                
                                enhancedPrompt.append("- ").append(toolName).append(": ").append(description).append("\\n");
                                
                                if (function.has("parameters")) {
                                    JsonObject params = function.getAsJsonObject("parameters");
                                    if (params.has("properties")) {
                                        JsonObject properties = params.getAsJsonObject("properties");
                                        enhancedPrompt.append("  Parameters: ");
                                        for (String paramName : properties.keySet()) {
                                            JsonObject param = properties.getAsJsonObject(paramName);
                                            String paramType = param.has("type") ? param.get("type").getAsString() : "string";
                                            String paramDesc = param.has("description") ? param.get("description").getAsString() : "";
                                            enhancedPrompt.append(paramName).append(" (").append(paramType).append(")");
                                            if (!paramDesc.isEmpty()) {
                                                enhancedPrompt.append(" - ").append(paramDesc);
                                            }
                                            enhancedPrompt.append(", ");
                                        }
                                        enhancedPrompt.setLength(enhancedPrompt.length() - 2); // Remove last comma
                                        enhancedPrompt.append("\\n");
                                    }
                                }
                                enhancedPrompt.append("\\n");
                            }
                        }
                        
                        enhancedPrompt.append("Important: Only use the TOOL_CALL format when you actually need to execute a tool. For regular conversation, respond normally.");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to add tool descriptions to prompt: " + e.getMessage());
                }
            }
            
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", enhancedPrompt.toString());
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
     * Parse text-based tool calls from AI response
     * @param response The AI response text
     * @return ToolCallParseResult with extracted tool calls and cleaned response
     */
    private ToolCallParseResult parseTextBasedToolCalls(String response) {
        List<ParsedToolCall> toolCalls = new ArrayList<>();
        boolean hasMalformedToolCall = false;
        
        // Pattern to match tool calls: TOOL_CALL: name, PARAMETERS: {json}, REASONING: text
        Pattern toolCallPattern = Pattern.compile(
            "TOOL_CALL:\\s*([^\\n]+)\\s*\\n" +
            "PARAMETERS:\\s*([^\\n]+)\\s*\\n" +
            "REASONING:\\s*([^\\n]+)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        
        Matcher matcher = toolCallPattern.matcher(response);
        StringBuilder cleanedResponse = new StringBuilder(response);
        
        while (matcher.find()) {
            String toolName = matcher.group(1).trim();
            String parametersStr = matcher.group(2).trim();
            String reasoning = matcher.group(3).trim();
            
            try {
                // Parse parameters JSON
                JsonObject parameters = gson.fromJson(parametersStr, JsonObject.class);
                
                // Create parsed tool call
                ParsedToolCall toolCall = new ParsedToolCall(toolName, parameters, reasoning);
                toolCalls.add(toolCall);
                
                plugin.getLogger().info("Parsed tool call: " + toolName + " with parameters: " + parametersStr);
                
                // Remove tool call from response text
                cleanedResponse = new StringBuilder(cleanedResponse.toString().replace(matcher.group(0), ""));
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse tool call parameters: " + parametersStr + " - " + e.getMessage());
                hasMalformedToolCall = true;
            }
        }
        
        // Check for malformed tool call attempts
        if (toolCalls.isEmpty() && (response.toLowerCase().contains("tool_call") || 
                                   response.toLowerCase().contains("parameters") ||
                                   response.toLowerCase().contains("reasoning"))) {
            hasMalformedToolCall = true;
        }
        
        return new ToolCallParseResult(toolCalls, cleanedResponse.toString().trim(), hasMalformedToolCall);
    }
    
    /**
     * Convert ParsedToolCall to MCP format for execution
     * @param toolCall The parsed tool call
     * @return JsonObject in MCP tool call format
     */
    private JsonObject convertToMCPToolCall(ParsedToolCall toolCall) {
        JsonObject mcpCall = new JsonObject();
        mcpCall.addProperty("name", toolCall.getToolName());
        mcpCall.add("arguments", toolCall.getParameters());
        return mcpCall;
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
        plugin.getLogger().info("Starting AI startup integration test...");
        plugin.getLogger().info("This test verifies AI and MCP functionality - checking Ollama connection and available tools");
        
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
                    
                    plugin.getLogger().info("✓ AI startup integration test completed successfully");
                    plugin.getLogger().info("AI services are operational and ready to handle player requests");
                } else {
                    plugin.getLogger().warning("✗ AI startup test failed - no response received from Ollama API");
                    plugin.getLogger().warning("Players may experience issues with AI functionality");
                }
                
            } catch (Exception e) {
                // Log the error but continue with graceful fallback
                plugin.getLogger().log(Level.WARNING, "✗ AI startup integration test failed - plugin will continue with reduced functionality", e);
                plugin.getLogger().warning("This usually indicates Ollama server issues or configuration problems");
                plugin.getLogger().warning("Check: 1) Ollama is running, 2) Model is available, 3) API URL is correct");
                
                // Create a fallback startup message
                String fallbackMessage = "Server has started! OllamaCraft plugin is active but AI services are currently unavailable. " +
                                       "Please check your Ollama configuration and try again later.";
                String formattedFallback = formatStartupResponse(fallbackMessage);
                broadcastStartupMessage(formattedFallback);
            }
        });
    }
    
    /**
     * Process the startup test conversation
     * @return The AI's response to the startup test
     */
    private String processStartupTest() throws Exception {
        plugin.getLogger().info("Processing startup test conversation with AI...");
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
            
            // Build and execute HTTP request with retry logic
            String responseBody = executeOllamaRequestWithRetry(requestBody);
            if (responseBody == null) {
                // Request failed after retries, return fallback message
                return "Hello! The server has started, but I'm currently experiencing technical difficulties connecting to my AI services. Basic server functionality is working normally.";
            } else if ("TOOL_FALLBACK_MODE_REQUIRED".equals(responseBody)) {
                // Model doesn't support native tool calling, use fallback mode for startup test
                plugin.getLogger().info("Startup test: Model doesn't support native tools, using fallback mode");
                return processStartupTestWithFallbackTools();
            }
            
            // Parse response
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("message")) {
                plugin.getLogger().warning("Startup test: Invalid Ollama API response format - missing message field");
                return "Hello! The server has started. I'm online but received an unexpected response format from my AI services.";
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
     * Process startup test using fallback tool calling for models that don't support native tools
     * @return The AI's response to the startup test
     */
    private String processStartupTestWithFallbackTools() throws Exception {
        plugin.getLogger().info("Processing startup test with fallback tool calling...");
        
        // Create request body with tool descriptions in system prompt
        JsonObject requestBody = buildChatRequestWithToolDescriptions();
        
        // Execute request without native tools
        String responseBody = executeOllamaRequestWithoutTools(requestBody);
        if (responseBody == null) {
            return "Hello! The server has started, but I'm experiencing technical difficulties with my AI services.";
        }
        
        // Parse response
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        
        if (!jsonResponse.has("message")) {
            plugin.getLogger().warning("Startup test: Invalid Ollama API response format - missing message field");
            return "Hello! The server has started. I'm online but received an unexpected response format from my AI services.";
        }
        
        JsonObject message = jsonResponse.getAsJsonObject("message");
        String aiResponse = message.has("content") ? message.get("content").getAsString() : "";
        
        // For startup test, we'll just return the AI response without parsing tool calls
        // The startup test is mainly to verify connectivity and basic functionality
        plugin.getLogger().info("Startup test with fallback tools completed successfully");
        return aiResponse;
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