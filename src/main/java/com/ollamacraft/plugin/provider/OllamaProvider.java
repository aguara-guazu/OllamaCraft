package com.ollamacraft.plugin.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ollamacraft.plugin.model.ChatMessage;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Ollama AI provider implementation
 * Maintains compatibility with existing Ollama functionality
 */
public class OllamaProvider extends BaseAIProvider {
    
    private static final String DEFAULT_MODEL = "llama3.1";
    private static final String DEFAULT_BASE_URL = "http://localhost:11434/api";
    
    // Fallback mode state
    private boolean useToolFallbackMode = false;
    private int consecutiveToolFailures = 0;
    private boolean toolCallsDisabled = false;
    
    public OllamaProvider(Logger logger) {
        super(logger);
        this.model = DEFAULT_MODEL;
        this.baseUrl = DEFAULT_BASE_URL;
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    @Override
    public List<String> getSupportedModels() {
        // Common Ollama models - this could be enhanced to query the API
        return Arrays.asList(
            "llama3.1", "llama3.1:70b", "llama3.1:405b",
            "llama3", "llama3:70b",
            "gemma2", "gemma2:27b",
            "mistral-nemo", "mistral-large",
            "codellama", "codellama:34b",
            "phi3", "phi3:medium",
            "qwen2", "qwen2:72b"
        );
    }
    
    @Override
    public boolean supportsNativeTools() {
        // Most modern Ollama models support tools, but we handle fallback
        return true;
    }
    
    @Override
    protected void configureProvider(Map<String, Object> config) {
        // Ollama-specific configuration
        if (baseUrl == null) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (model == null) {
            model = DEFAULT_MODEL;
        }
        
        debug("Configured Ollama provider - URL: " + baseUrl + ", Model: " + model);
    }
    
    @Override
    public CompletableFuture<AIResponse> chat(List<ChatMessage> messages, String systemPrompt) {
        return chatWithTools(messages, systemPrompt, new ArrayList<>());
    }
    
    @Override
    public CompletableFuture<AIResponse> chatWithTools(List<ChatMessage> messages, String systemPrompt, List<JsonObject> tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateConfiguration();
                
                // Create a local copy to avoid lambda variable issues
                List<JsonObject> effectiveTools = tools;
                
                // Check if tools are disabled due to previous failures
                if (toolCallsDisabled && !effectiveTools.isEmpty()) {
                    debug("Tool calling is disabled due to previous failures, removing tools");
                    effectiveTools = new ArrayList<>();
                }
                
                // Build request
                JsonObject request = buildChatRequest(messages, systemPrompt, effectiveTools);
                
                // Execute request
                String responseBody = executeOllamaRequest(request);
                if (responseBody == null) {
                    return AIResponse.error("Failed to get response from Ollama after retries", getProviderName());
                }
                
                // Handle fallback mode detection
                if ("TOOL_FALLBACK_MODE_REQUIRED".equals(responseBody)) {
                    debug("Model doesn't support native tools, using fallback mode");
                    useToolFallbackMode = true;
                    return chatWithFallbackTools(messages, systemPrompt, effectiveTools);
                }
                
                // Parse response
                return parseOllamaResponse(responseBody);
                
            } catch (Exception e) {
                error("Error in chat request", e);
                return AIResponse.error("Error processing chat request: " + e.getMessage(), getProviderName());
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                debug("Testing connection to Ollama server");
                
                JsonObject testRequest = new JsonObject();
                testRequest.addProperty("model", model);
                testRequest.addProperty("stream", false);
                
                JsonArray messages = new JsonArray();
                JsonObject testMessage = new JsonObject();
                testMessage.addProperty("role", "user");
                testMessage.addProperty("content", "Hello");
                messages.add(testMessage);
                testRequest.add("messages", messages);
                
                String response = executeOllamaRequest(testRequest);
                boolean success = response != null && !response.equals("TOOL_FALLBACK_MODE_REQUIRED");
                
                if (success) {
                    debug("Connection test successful");
                } else {
                    warn("Connection test failed");
                }
                
                return success;
                
            } catch (Exception e) {
                error("Connection test failed", e);
                return false;
            }
        });
    }
    
    /**
     * Build Ollama chat request
     */
    private JsonObject buildChatRequest(List<ChatMessage> messages, String systemPrompt, List<JsonObject> tools) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("temperature", temperature);
        request.addProperty("stream", false);
        
        // Build messages array
        JsonArray messageArray = new JsonArray();
        
        // Add system message if provided
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messageArray.add(systemMessage);
        }
        
        // Add chat messages
        for (ChatMessage message : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", message.getRole());
            msgObj.addProperty("content", message.getContent());
            messageArray.add(msgObj);
        }
        
        request.add("messages", messageArray);
        
        // Add tools if available and not in fallback mode
        if (!tools.isEmpty() && !useToolFallbackMode) {
            JsonArray toolsArray = new JsonArray();
            for (JsonObject tool : tools) {
                toolsArray.add(tool);
            }
            request.add("tools", toolsArray);
        }
        
        return request;
    }
    
    /**
     * Execute Ollama API request with retry logic
     */
    private String executeOllamaRequest(JsonObject requestBody) {
        debug("Starting Ollama API request to " + baseUrl + "/chat");
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                debug("Ollama API request attempt " + attempt + "/" + maxRetries);
                
                Request request = new Request.Builder()
                    .url(baseUrl + "/chat")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            debug("Ollama API request successful on attempt " + attempt);
                            return body.string();
                        }
                    } else {
                        String statusMessage = response.message();
                        warn("Ollama API HTTP " + response.code() + " error (attempt " + attempt + "/" + maxRetries + "): " + statusMessage);
                        
                        // Check for tool support error
                        try {
                            ResponseBody errorBody = response.body();
                            if (errorBody != null) {
                                String errorContent = errorBody.string();
                                if (errorContent.contains("does not support tools")) {
                                    warn("Model does not support native tool calling - switching to fallback mode");
                                    useToolFallbackMode = true;
                                    return "TOOL_FALLBACK_MODE_REQUIRED";
                                }
                            }
                        } catch (Exception e) {
                            warn("Could not read error response body");
                        }
                        
                        // Non-recoverable errors
                        if (response.code() == 401 || response.code() == 403 || response.code() == 404) {
                            error("Non-recoverable Ollama API error: HTTP " + response.code());
                            return null;
                        }
                    }
                }
                
            } catch (IOException e) {
                warn("Ollama API connection error (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
                
                if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                    error("Ollama server appears to be offline");
                    return null;
                }
            } catch (Exception e) {
                warn("Unexpected error during Ollama API request (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
            }
            
            // Wait before retrying
            if (attempt < maxRetries) {
                try {
                    int delay = 1000 * (int) Math.pow(2, attempt - 1);
                    debug("Retrying Ollama request in " + delay + "ms...");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    warn("Retry delay interrupted");
                    break;
                }
            }
        }
        
        error("Ollama API request failed after " + maxRetries + " attempts");
        return null;
    }
    
    /**
     * Parse Ollama response
     */
    private AIResponse parseOllamaResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("message")) {
                return AIResponse.error("Invalid Ollama response format: missing message", getProviderName());
            }
            
            JsonObject message = jsonResponse.getAsJsonObject("message");
            String content = message.has("content") ? message.get("content").getAsString() : "";
            
            // Check for tool calls
            if (message.has("tool_calls")) {
                JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                List<ToolCall> toolCalls = parseOllamaToolCalls(toolCallsArray);
                return AIResponse.successWithTools(content, toolCalls, getProviderName(), model);
            }
            
            // Regular response
            if (content.trim().isEmpty()) {
                warn("Ollama returned empty response, providing fallback message");
                content = "I'm sorry, I'm having trouble generating a response right now. Could you please try rephrasing your question?";
            }
            
            return AIResponse.success(content, getProviderName(), model);
            
        } catch (Exception e) {
            error("Failed to parse Ollama response", e);
            return AIResponse.error("Failed to parse response: " + e.getMessage(), getProviderName());
        }
    }
    
    /**
     * Parse tool calls from Ollama response
     */
    private List<ToolCall> parseOllamaToolCalls(JsonArray toolCallsArray) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        for (JsonElement element : toolCallsArray) {
            try {
                JsonObject toolCallObj = element.getAsJsonObject();
                
                if (toolCallObj.has("function")) {
                    JsonObject function = toolCallObj.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    JsonObject arguments = function.has("arguments") ? 
                        function.getAsJsonObject("arguments") : new JsonObject();
                    
                    String id = toolCallObj.has("id") ? toolCallObj.get("id").getAsString() : null;
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            } catch (Exception e) {
                warn("Failed to parse tool call: " + e.getMessage());
            }
        }
        
        return toolCalls;
    }
    
    /**
     * Handle chat with fallback tools for models that don't support native tools
     */
    private AIResponse chatWithFallbackTools(List<ChatMessage> messages, String systemPrompt, List<JsonObject> tools) {
        // For now, return a simple response - this would need the full fallback implementation
        // from the existing AIService
        warn("Fallback tool calling not fully implemented yet in provider");
        return AIResponse.success("I'm currently in fallback mode for tool calling. Basic chat is available.", getProviderName(), model);
    }
    
    @Override
    protected List<Object> convertMessages(List<ChatMessage> messages) {
        // Already implemented in buildChatRequest
        return new ArrayList<>();
    }
    
    @Override
    protected List<Object> convertTools(List<JsonObject> tools) {
        // Already implemented in buildChatRequest  
        return new ArrayList<>();
    }
    
    @Override
    protected List<ToolCall> parseToolCalls(Object response) {
        // Already implemented in parseOllamaResponse
        return new ArrayList<>();
    }
    
    @Override
    protected Object executeWithRetry(Object request) {
        // Already implemented in executeOllamaRequest
        return null;
    }
    
    /**
     * Reset tool calling state
     */
    public void resetToolState() {
        consecutiveToolFailures = 0;
        toolCallsDisabled = false;
        useToolFallbackMode = false;
        debug("Tool calling state has been reset");
    }
}