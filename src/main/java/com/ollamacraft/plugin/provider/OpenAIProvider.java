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
 * OpenAI AI provider implementation
 * Supports GPT-4, GPT-4o, and other OpenAI models
 */
public class OpenAIProvider extends BaseAIProvider {
    
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    
    private String organization; // Optional OpenAI organization ID
    
    public OpenAIProvider(Logger logger) {
        super(logger);
        this.model = DEFAULT_MODEL;
        this.baseUrl = DEFAULT_BASE_URL;
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public List<String> getSupportedModels() {
        return Arrays.asList(
            "gpt-4o",
            "gpt-4o-mini", 
            "gpt-4-turbo",
            "gpt-4",
            "gpt-3.5-turbo",
            "gpt-3.5-turbo-16k"
        );
    }
    
    @Override
    public boolean supportsNativeTools() {
        return true; // OpenAI has excellent function calling support
    }
    
    @Override
    protected void configureProvider(Map<String, Object> config) {
        if (baseUrl == null) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (model == null) {
            model = DEFAULT_MODEL;
        }
        
        // Optional organization ID
        if (config.containsKey("organization")) {
            this.organization = (String) config.get("organization");
        }
        
        // Validate API key is present (but don't require it during configuration)
        // The actual validation will happen during requests
        debug("Configured OpenAI provider - URL: " + baseUrl + ", Model: " + model);
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
                JsonObject request = buildOpenAIRequest(messages, systemPrompt, tools);
                
                // Execute request
                String responseBody = executeOpenAIRequest(request);
                if (responseBody == null) {
                    return AIResponse.error("Failed to get response from OpenAI after retries", getProviderName());
                }
                
                // Parse response
                return parseOpenAIResponse(responseBody);
                
            } catch (Exception e) {
                error("Error in OpenAI chat request", e);
                return AIResponse.error("Error processing chat request: " + e.getMessage(), getProviderName());
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                debug("Testing connection to OpenAI API");
                
                JsonObject testRequest = new JsonObject();
                testRequest.addProperty("model", model);
                testRequest.addProperty("max_tokens", 10);
                
                JsonArray messages = new JsonArray();
                JsonObject testMessage = new JsonObject();
                testMessage.addProperty("role", "user");
                testMessage.addProperty("content", "Hello");
                messages.add(testMessage);
                testRequest.add("messages", messages);
                
                String response = executeOpenAIRequest(testRequest);
                boolean success = response != null;
                
                if (success) {
                    debug("OpenAI connection test successful");
                } else {
                    warn("OpenAI connection test failed");
                }
                
                return success;
                
            } catch (Exception e) {
                error("OpenAI connection test failed", e);
                return false;
            }
        });
    }
    
    /**
     * Build OpenAI API request
     */
    private JsonObject buildOpenAIRequest(List<ChatMessage> messages, String systemPrompt, List<JsonObject> tools) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("temperature", temperature);
        request.addProperty("max_tokens", 4096);
        
        // Build messages array
        JsonArray messageArray = new JsonArray();
        
        // Add system message if provided
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messageArray.add(systemMessage);
        }
        
        // Convert messages to OpenAI format
        for (ChatMessage message : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", convertRoleForOpenAI(message.getRole()));
            msgObj.addProperty("content", message.getContent());
            messageArray.add(msgObj);
        }
        
        request.add("messages", messageArray);
        
        // Add tools if available (tools are now pre-converted by tool converter system)
        if (!tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (JsonObject tool : tools) {
                toolsArray.add(tool);
            }
            request.add("tools", toolsArray);
            request.addProperty("tool_choice", "auto");
        }
        
        return request;
    }
    
    /**
     * Convert message role to OpenAI format
     */
    private String convertRoleForOpenAI(String role) {
        switch (role) {
            case "assistant":
                return "assistant";
            case "tool":
                return "tool";
            case "system":
                return "system";
            case "user":
            default:
                return "user";
        }
    }
    
    
    /**
     * Execute OpenAI API request with retry logic
     */
    private String executeOpenAIRequest(JsonObject requestBody) {
        debug("Starting OpenAI API request to " + baseUrl + "/chat/completions");
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                debug("OpenAI API request attempt " + attempt + "/" + maxRetries);
                
                Request.Builder requestBuilder = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json");
                
                // Add organization header if specified
                if (organization != null && !organization.trim().isEmpty()) {
                    requestBuilder.addHeader("OpenAI-Organization", organization);
                }
                
                Request request = requestBuilder.build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            debug("OpenAI API request successful on attempt " + attempt);
                            return body.string();
                        }
                    } else {
                        String statusMessage = response.message();
                        warn("OpenAI API HTTP " + response.code() + " error (attempt " + attempt + "/" + maxRetries + "): " + statusMessage);
                        
                        // Log error body for debugging
                        try {
                            ResponseBody errorBody = response.body();
                            if (errorBody != null) {
                                String errorContent = errorBody.string();
                                warn("OpenAI API error response: " + errorContent);
                            }
                        } catch (Exception e) {
                            warn("Could not read OpenAI error response");
                        }
                        
                        // Non-recoverable errors
                        if (response.code() == 401) {
                            error("OpenAI API authentication failed - check API key");
                            return null;
                        }
                        if (response.code() == 403) {
                            error("OpenAI API access forbidden - check permissions");
                            return null;
                        }
                        if (response.code() == 400) {
                            error("OpenAI API bad request - check request format");
                            return null;
                        }
                        if (response.code() == 429) {
                            warn("OpenAI API rate limit exceeded - will retry");
                        }
                    }
                }
                
            } catch (IOException e) {
                warn("OpenAI API connection error (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
            } catch (Exception e) {
                warn("Unexpected error during OpenAI API request (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
            }
            
            // Wait before retrying (longer delays for rate limits)
            if (attempt < maxRetries) {
                try {
                    int delay = 1000 * (int) Math.pow(2, attempt - 1);
                    debug("Retrying OpenAI request in " + delay + "ms...");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    warn("OpenAI retry delay interrupted");
                    break;
                }
            }
        }
        
        error("OpenAI API request failed after " + maxRetries + " attempts");
        return null;
    }
    
    /**
     * Parse OpenAI response
     */
    private AIResponse parseOpenAIResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("choices")) {
                return AIResponse.error("Invalid OpenAI response format: missing choices", getProviderName());
            }
            
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            if (choices.size() == 0) {
                return AIResponse.error("OpenAI response has no choices", getProviderName());
            }
            
            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            
            String content = message.has("content") && !message.get("content").isJsonNull() ? 
                message.get("content").getAsString() : "";
            
            // Check for tool calls
            if (message.has("tool_calls")) {
                JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                List<ToolCall> toolCalls = parseOpenAIToolCalls(toolCallsArray);
                return AIResponse.successWithTools(content, toolCalls, getProviderName(), model);
            }
            
            // Regular response
            if (content.trim().isEmpty()) {
                warn("OpenAI returned empty response, providing fallback message");
                content = "I'm sorry, I'm having trouble generating a response right now. Could you please try rephrasing your question?";
            }
            
            return AIResponse.success(content, getProviderName(), model);
            
        } catch (Exception e) {
            error("Failed to parse OpenAI response", e);
            return AIResponse.error("Failed to parse response: " + e.getMessage(), getProviderName());
        }
    }
    
    /**
     * Parse tool calls from OpenAI response
     */
    private List<ToolCall> parseOpenAIToolCalls(JsonArray toolCallsArray) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        for (JsonElement element : toolCallsArray) {
            try {
                JsonObject toolCallObj = element.getAsJsonObject();
                
                String id = toolCallObj.get("id").getAsString();
                String type = toolCallObj.get("type").getAsString();
                
                if ("function".equals(type) && toolCallObj.has("function")) {
                    JsonObject function = toolCallObj.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    
                    JsonObject arguments = new JsonObject();
                    if (function.has("arguments")) {
                        String argsString = function.get("arguments").getAsString();
                        if (!argsString.trim().isEmpty()) {
                            try {
                                arguments = gson.fromJson(argsString, JsonObject.class);
                            } catch (Exception e) {
                                warn("Failed to parse tool arguments: " + argsString);
                            }
                        }
                    }
                    
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            } catch (Exception e) {
                warn("Failed to parse OpenAI tool call: " + e.getMessage());
            }
        }
        
        return toolCalls;
    }
    
    @Override
    protected List<Object> convertMessages(List<ChatMessage> messages) {
        // Already implemented in buildOpenAIRequest
        return new ArrayList<>();
    }
    
    @Override
    protected List<Object> convertTools(List<JsonObject> tools) {
        // Already implemented in buildOpenAIRequest
        return new ArrayList<>();
    }
    
    @Override
    protected List<ToolCall> parseToolCalls(Object response) {
        // Already implemented in parseOpenAIResponse
        return new ArrayList<>();
    }
    
    @Override
    protected Object executeWithRetry(Object request) {
        // Already implemented in executeOpenAIRequest
        return null;
    }
}