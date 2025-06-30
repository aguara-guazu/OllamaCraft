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
 * Claude (Anthropic) AI provider implementation
 * Supports Claude 3.5 Sonnet and other Anthropic models
 */
public class ClaudeProvider extends BaseAIProvider {
    
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    
    public ClaudeProvider(Logger logger) {
        super(logger);
        this.model = DEFAULT_MODEL;
        this.baseUrl = DEFAULT_BASE_URL;
    }
    
    @Override
    public String getProviderName() {
        return "claude";
    }
    
    @Override
    public List<String> getSupportedModels() {
        return Arrays.asList(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022", 
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307",
            "claude-sonnet-4-20250514",
            "claude-opus-4-20250514"
        );
    }
    
    @Override
    public boolean supportsNativeTools() {
        return true; // Claude has excellent native tool support
    }
    
    @Override
    protected void configureProvider(Map<String, Object> config) {
        if (baseUrl == null) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (model == null) {
            model = DEFAULT_MODEL;
        }
        
        // Validate API key is present (but don't require it during configuration)
        // The actual validation will happen during requests
        debug("Configured Claude provider - URL: " + baseUrl + ", Model: " + model);
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
                
                // Build request
                JsonObject request = buildClaudeRequest(messages, systemPrompt, tools);
                
                // Execute request
                String responseBody = executeClaudeRequest(request);
                if (responseBody == null) {
                    return AIResponse.error("Failed to get response from Claude after retries", getProviderName());
                }
                
                // Parse response
                return parseClaudeResponse(responseBody);
                
            } catch (Exception e) {
                error("Error in Claude chat request", e);
                return AIResponse.error("Error processing chat request: " + e.getMessage(), getProviderName());
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                debug("Testing connection to Claude API");
                
                JsonObject testRequest = new JsonObject();
                testRequest.addProperty("model", model);
                testRequest.addProperty("max_tokens", 10);
                
                JsonArray messages = new JsonArray();
                JsonObject testMessage = new JsonObject();
                testMessage.addProperty("role", "user");
                testMessage.addProperty("content", "Hello");
                messages.add(testMessage);
                testRequest.add("messages", messages);
                
                String response = executeClaudeRequest(testRequest);
                boolean success = response != null;
                
                if (success) {
                    debug("Claude connection test successful");
                } else {
                    warn("Claude connection test failed");
                }
                
                return success;
                
            } catch (Exception e) {
                error("Claude connection test failed", e);
                return false;
            }
        });
    }
    
    /**
     * Build Claude API request
     */
    private JsonObject buildClaudeRequest(List<ChatMessage> messages, String systemPrompt, List<JsonObject> tools) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("max_tokens", 4096);
        request.addProperty("temperature", temperature);
        
        // Claude uses separate system field
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            request.addProperty("system", systemPrompt);
        }
        
        // Convert messages to Claude format
        JsonArray messageArray = new JsonArray();
        for (ChatMessage message : messages) {
            // Skip system messages as they're handled separately
            if (!"system".equals(message.getRole())) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("role", convertRoleForClaude(message.getRole()));
                msgObj.addProperty("content", message.getContent());
                messageArray.add(msgObj);
            }
        }
        
        request.add("messages", messageArray);
        
        // Add tools if available (tools are now pre-converted by tool converter system)
        if (!tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (JsonObject tool : tools) {
                toolsArray.add(tool);
            }
            request.add("tools", toolsArray);
            
            // Claude requires tool_choice as an object, not a string
            JsonObject toolChoice = new JsonObject();
            toolChoice.addProperty("type", "auto");
            request.add("tool_choice", toolChoice);
        }
        
        return request;
    }
    
    /**
     * Convert message role to Claude format
     */
    private String convertRoleForClaude(String role) {
        switch (role) {
            case "assistant":
                return "assistant";
            case "tool":
                return "user"; // Tool results are sent as user messages in Claude
            case "user":
            default:
                return "user";
        }
    }
    
    
    /**
     * Execute Claude API request with retry logic
     */
    private String executeClaudeRequest(JsonObject requestBody) {
        debug("Starting Claude API request to " + baseUrl + "/messages");
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                debug("Claude API request attempt " + attempt + "/" + maxRetries);
                
                Request request = new Request.Builder()
                    .url(baseUrl + "/messages")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .addHeader("Content-Type", "application/json")
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            debug("Claude API request successful on attempt " + attempt);
                            return body.string();
                        }
                    } else {
                        String statusMessage = response.message();
                        warn("Claude API HTTP " + response.code() + " error (attempt " + attempt + "/" + maxRetries + "): " + statusMessage);
                        
                        // Log error body for debugging
                        try {
                            ResponseBody errorBody = response.body();
                            if (errorBody != null) {
                                String errorContent = errorBody.string();
                                warn("Claude API error response: " + errorContent);
                            }
                        } catch (Exception e) {
                            warn("Could not read Claude error response");
                        }
                        
                        // Non-recoverable errors
                        if (response.code() == 401 || response.code() == 403) {
                            error("Claude API authentication failed - check API key");
                            return null;
                        }
                        if (response.code() == 400) {
                            error("Claude API bad request - check request format");
                            return null;
                        }
                    }
                }
                
            } catch (IOException e) {
                warn("Claude API connection error (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
            } catch (Exception e) {
                warn("Unexpected error during Claude API request (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
            }
            
            // Wait before retrying
            if (attempt < maxRetries) {
                try {
                    int delay = 1000 * (int) Math.pow(2, attempt - 1);
                    debug("Retrying Claude request in " + delay + "ms...");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    warn("Claude retry delay interrupted");
                    break;
                }
            }
        }
        
        error("Claude API request failed after " + maxRetries + " attempts");
        return null;
    }
    
    /**
     * Parse Claude response
     */
    private AIResponse parseClaudeResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("content")) {
                return AIResponse.error("Invalid Claude response format: missing content", getProviderName());
            }
            
            JsonArray contentArray = jsonResponse.getAsJsonArray("content");
            StringBuilder content = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();
            
            // Process content blocks
            for (JsonElement element : contentArray) {
                JsonObject block = element.getAsJsonObject();
                String type = block.get("type").getAsString();
                
                if ("text".equals(type)) {
                    content.append(block.get("text").getAsString());
                } else if ("tool_use".equals(type)) {
                    // Parse tool call
                    String id = block.get("id").getAsString();
                    String name = block.get("name").getAsString();
                    JsonObject input = block.has("input") ? 
                        block.getAsJsonObject("input") : new JsonObject();
                    
                    toolCalls.add(new ToolCall(id, name, input));
                }
            }
            
            String responseText = content.toString().trim();
            
            // Handle tool calls
            if (!toolCalls.isEmpty()) {
                debug("Claude returned " + toolCalls.size() + " tool calls");
                return AIResponse.successWithTools(responseText, toolCalls, getProviderName(), model);
            }
            
            // Regular response
            if (responseText.isEmpty()) {
                warn("Claude returned empty response, providing fallback message");
                responseText = "I'm sorry, I'm having trouble generating a response right now. Could you please try rephrasing your question?";
            }
            
            return AIResponse.success(responseText, getProviderName(), model);
            
        } catch (Exception e) {
            error("Failed to parse Claude response", e);
            return AIResponse.error("Failed to parse response: " + e.getMessage(), getProviderName());
        }
    }
    
    @Override
    protected List<Object> convertMessages(List<ChatMessage> messages) {
        // Already implemented in buildClaudeRequest
        return new ArrayList<>();
    }
    
    @Override
    protected List<Object> convertTools(List<JsonObject> tools) {
        // Already implemented in buildClaudeRequest
        return new ArrayList<>();
    }
    
    @Override
    protected List<ToolCall> parseToolCalls(Object response) {
        // Already implemented in parseClaudeResponse
        return new ArrayList<>();
    }
    
    @Override
    protected Object executeWithRetry(Object request) {
        // Already implemented in executeClaudeRequest
        return null;
    }
}