package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes MCP tool calls requested by the Ollama AI model
 */
public class MCPToolExecutor {
    
    private final MCPHttpClient httpClient;
    private final MCPToolProvider toolProvider;
    private final Logger logger;
    private final Gson gson;
    
    /**
     * Create a new MCP tool executor
     * @param httpClient The HTTP client for MCP communication
     * @param toolProvider The tool provider for tool definitions
     * @param logger Logger for debugging
     */
    public MCPToolExecutor(MCPHttpClient httpClient, MCPToolProvider toolProvider, Logger logger) {
        this.httpClient = httpClient;
        this.toolProvider = toolProvider;
        this.logger = logger;
        this.gson = new Gson();
    }
    
    /**
     * Execute a tool call from Ollama
     * @param toolCall The tool call from Ollama
     * @return CompletableFuture with the tool execution result
     */
    public CompletableFuture<JsonObject> executeToolCall(JsonObject toolCall) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                debug("Executing tool call: " + toolCall);
                
                // Extract tool information from Ollama tool call
                if (!toolCall.has("function")) {
                    throw new RuntimeException("Tool call missing function information");
                }
                
                JsonObject function = toolCall.getAsJsonObject("function");
                if (!function.has("name")) {
                    throw new RuntimeException("Tool call missing function name");
                }
                
                String toolName = function.get("name").getAsString();
                JsonObject arguments = function.has("arguments") ? 
                    function.getAsJsonObject("arguments") : new JsonObject();
                
                // Get the original MCP tool definition
                JsonObject mcpTool = toolProvider.getMCPTool(toolName);
                if (mcpTool == null) {
                    throw new RuntimeException("Tool not found: " + toolName);
                }
                
                // Create MCP tool call request
                JsonObject mcpRequest = new JsonObject();
                mcpRequest.addProperty("jsonrpc", "2.0");
                mcpRequest.addProperty("id", "tool-call-" + System.currentTimeMillis());
                mcpRequest.addProperty("method", "tools/call");
                
                JsonObject params = new JsonObject();
                params.addProperty("name", toolName);
                params.add("arguments", arguments);
                mcpRequest.add("params", params);
                
                debug("Sending MCP tool call: " + mcpRequest);
                
                // Execute the tool call
                JsonObject mcpResponse = httpClient.sendMessage(mcpRequest).get(60, TimeUnit.SECONDS);
                
                debug("Received MCP response: " + mcpResponse);
                
                // Convert MCP response to Ollama format
                JsonObject ollamaResult = convertMCPResponseToOllama(mcpResponse, toolCall);
                
                debug("Converted to Ollama result: " + ollamaResult);
                
                return ollamaResult;
                
            } catch (Exception e) {
                if (logger != null) {
                    logger.log(Level.WARNING, "[MCPToolExecutor] Error executing tool call", e);
                }
                
                // Return error result in Ollama format
                return createErrorResult(toolCall, e.getMessage());
            }
        });
    }
    
    /**
     * Convert MCP response to Ollama tool result format
     * @param mcpResponse The MCP response
     * @param originalToolCall The original tool call from Ollama
     * @return Ollama tool result
     */
    private JsonObject convertMCPResponseToOllama(JsonObject mcpResponse, JsonObject originalToolCall) {
        JsonObject result = new JsonObject();
        
        // Copy tool call ID if present
        if (originalToolCall.has("id")) {
            result.add("id", originalToolCall.get("id"));
        }
        
        result.addProperty("type", "function");
        
        if (mcpResponse.has("error")) {
            // Handle MCP error
            JsonObject error = mcpResponse.getAsJsonObject("error");
            String errorMessage = error.has("message") ? 
                error.get("message").getAsString() : 
                "Unknown MCP error";
            
            result.addProperty("content", "Error: " + errorMessage);
        } else if (mcpResponse.has("result")) {
            // Handle successful result
            JsonObject mcpResult = mcpResponse.getAsJsonObject("result");
            
            if (mcpResult.has("content")) {
                JsonElement content = mcpResult.get("content");
                
                if (content.isJsonArray()) {
                    // Handle array of content blocks
                    StringBuilder contentText = new StringBuilder();
                    for (JsonElement contentElement : content.getAsJsonArray()) {
                        if (contentElement.isJsonObject()) {
                            JsonObject contentBlock = contentElement.getAsJsonObject();
                            if (contentBlock.has("text")) {
                                contentText.append(contentBlock.get("text").getAsString()).append("\n");
                            } else if (contentBlock.has("type") && contentBlock.has("data")) {
                                String type = contentBlock.get("type").getAsString();
                                contentText.append("[").append(type).append(" content]\n");
                            }
                        } else {
                            contentText.append(contentElement.getAsString()).append("\n");
                        }
                    }
                    result.addProperty("content", contentText.toString().trim());
                } else if (content.isJsonObject()) {
                    // Handle single content block
                    JsonObject contentBlock = content.getAsJsonObject();
                    if (contentBlock.has("text")) {
                        result.addProperty("content", contentBlock.get("text").getAsString());
                    } else {
                        result.addProperty("content", contentBlock.toString());
                    }
                } else {
                    // Handle string content
                    result.addProperty("content", content.getAsString());
                }
            } else {
                // Fallback: convert entire result to string
                result.addProperty("content", mcpResult.toString());
            }
            
            // Add additional metadata if present
            if (mcpResult.has("isError") && mcpResult.get("isError").getAsBoolean()) {
                String content = result.get("content").getAsString();
                result.addProperty("content", "Error: " + content);
            }
        } else {
            result.addProperty("content", "No result from MCP server");
        }
        
        return result;
    }
    
    /**
     * Create an error result in Ollama format
     * @param originalToolCall The original tool call
     * @param errorMessage The error message
     * @return Error result
     */
    private JsonObject createErrorResult(JsonObject originalToolCall, String errorMessage) {
        JsonObject result = new JsonObject();
        
        if (originalToolCall.has("id")) {
            result.add("id", originalToolCall.get("id"));
        }
        
        result.addProperty("type", "function");
        result.addProperty("content", "Tool execution failed: " + errorMessage);
        
        return result;
    }
    
    /**
     * Validate if a tool call has the required structure
     * @param toolCall The tool call to validate
     * @return True if valid
     */
    public boolean isValidToolCall(JsonObject toolCall) {
        try {
            return toolCall.has("function") &&
                   toolCall.getAsJsonObject("function").has("name");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Log debug message if logger is available
     */
    private void debug(String message) {
        if (logger != null) {
            logger.info("[MCPToolExecutor] " + message);
        }
    }
}