package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified response from AI providers
 * Standardizes responses from different AI services
 */
public class AIResponse {
    
    private final String content;
    private final List<ToolCall> toolCalls;
    private final boolean successful;
    private final String errorMessage;
    private final String providerName;
    private final String model;
    
    private AIResponse(Builder builder) {
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
        this.successful = builder.successful;
        this.errorMessage = builder.errorMessage;
        this.providerName = builder.providerName;
        this.model = builder.model;
    }
    
    /**
     * Get the AI response content
     * @return Response text content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Get tool calls requested by the AI
     * @return List of tool calls
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls != null ? toolCalls : new ArrayList<>();
    }
    
    /**
     * Check if the response was successful
     * @return true if successful
     */
    public boolean isSuccessful() {
        return successful;
    }
    
    /**
     * Get error message if request failed
     * @return Error message or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Get the provider that generated this response
     * @return Provider name
     */
    public String getProviderName() {
        return providerName;
    }
    
    /**
     * Get the model that generated this response
     * @return Model name
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Check if this response contains tool calls
     * @return true if there are tool calls
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    /**
     * Check if response has content
     * @return true if content is not empty
     */
    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }
    
    /**
     * Create a successful response
     * @param content Response content
     * @param providerName Provider name
     * @param model Model name
     * @return AIResponse instance
     */
    public static AIResponse success(String content, String providerName, String model) {
        return new Builder()
            .content(content)
            .successful(true)
            .providerName(providerName)
            .model(model)
            .build();
    }
    
    /**
     * Create a successful response with tool calls
     * @param content Response content
     * @param toolCalls Tool calls
     * @param providerName Provider name
     * @param model Model name
     * @return AIResponse instance
     */
    public static AIResponse successWithTools(String content, List<ToolCall> toolCalls, String providerName, String model) {
        return new Builder()
            .content(content)
            .toolCalls(toolCalls)
            .successful(true)
            .providerName(providerName)
            .model(model)
            .build();
    }
    
    /**
     * Create an error response
     * @param errorMessage Error message
     * @param providerName Provider name
     * @return AIResponse instance
     */
    public static AIResponse error(String errorMessage, String providerName) {
        return new Builder()
            .successful(false)
            .errorMessage(errorMessage)
            .providerName(providerName)
            .build();
    }
    
    /**
     * Builder for AIResponse
     */
    public static class Builder {
        private String content;
        private List<ToolCall> toolCalls;
        private boolean successful;
        private String errorMessage;
        private String providerName;
        private String model;
        
        public Builder content(String content) {
            this.content = content;
            return this;
        }
        
        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }
        
        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public AIResponse build() {
            return new AIResponse(this);
        }
    }
}