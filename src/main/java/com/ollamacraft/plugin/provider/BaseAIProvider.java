package com.ollamacraft.plugin.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ollamacraft.plugin.model.ChatMessage;
import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Base implementation for AI providers
 * Contains common functionality shared across all providers
 */
public abstract class BaseAIProvider implements AIProvider {
    
    protected final Logger logger;
    protected final Gson gson;
    protected OkHttpClient httpClient;
    
    // Configuration
    protected String model;
    protected double temperature;
    protected String apiKey;
    protected String baseUrl;
    protected int maxRetries;
    protected int timeoutSeconds;
    
    public BaseAIProvider(Logger logger) {
        this.logger = logger;
        this.gson = new Gson();
        this.temperature = 0.7;
        this.maxRetries = 3;
        this.timeoutSeconds = 60;
        initializeHttpClient();
    }
    
    /**
     * Initialize HTTP client with default settings
     */
    protected void initializeHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public void configure(Map<String, Object> config) {
        if (config.containsKey("model")) {
            this.model = (String) config.get("model");
        }
        if (config.containsKey("temperature")) {
            this.temperature = ((Number) config.get("temperature")).doubleValue();
        }
        if (config.containsKey("api-key")) {
            this.apiKey = (String) config.get("api-key");
        }
        if (config.containsKey("base-url")) {
            this.baseUrl = (String) config.get("base-url");
        }
        if (config.containsKey("timeout-seconds")) {
            this.timeoutSeconds = ((Number) config.get("timeout-seconds")).intValue();
            // Recreate HTTP client with new timeout
            initializeHttpClient();
        }
        if (config.containsKey("max-retries")) {
            this.maxRetries = ((Number) config.get("max-retries")).intValue();
        }
        
        // Call provider-specific configuration
        configureProvider(config);
    }
    
    /**
     * Provider-specific configuration override
     * @param config Configuration map
     */
    protected abstract void configureProvider(Map<String, Object> config);
    
    @Override
    public String getCurrentModel() {
        return model;
    }
    
    @Override
    public void setModel(String model) {
        this.model = model;
    }
    
    @Override
    public double getTemperature() {
        return temperature;
    }
    
    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    @Override
    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
    
    /**
     * Log debug information if enabled
     * @param message Debug message
     */
    protected void debug(String message) {
        logger.info("[" + getProviderName() + "] " + message);
    }
    
    /**
     * Log warning message
     * @param message Warning message
     */
    protected void warn(String message) {
        logger.warning("[" + getProviderName() + "] " + message);
    }
    
    /**
     * Log error message
     * @param message Error message
     */
    protected void error(String message) {
        logger.severe("[" + getProviderName() + "] " + message);
    }
    
    /**
     * Log error with exception
     * @param message Error message
     * @param throwable Exception
     */
    protected void error(String message, Throwable throwable) {
        logger.severe("[" + getProviderName() + "] " + message + ": " + throwable.getMessage());
    }
    
    /**
     * Validate that the provider is properly configured
     * @throws IllegalStateException if not properly configured
     */
    protected void validateConfiguration() {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalStateException("Model is required for " + getProviderName());
        }
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Base URL is required for " + getProviderName());
        }
    }
    
    /**
     * Build system prompt with agent name replacement
     * @param systemPrompt Original system prompt
     * @param agentName Agent name to replace
     * @return Processed system prompt
     */
    protected String buildSystemPrompt(String systemPrompt, String agentName) {
        if (systemPrompt == null) {
            return "You are " + agentName + ", a helpful assistant.";
        }
        return systemPrompt.replace("{agent_name}", agentName);
    }
    
    /**
     * Convert ChatMessage list to provider-specific format
     * @param messages List of chat messages
     * @return List in provider format
     */
    protected abstract List<Object> convertMessages(List<ChatMessage> messages);
    
    /**
     * Convert tools to provider-specific format
     * @param tools List of MCP tools
     * @return List in provider format
     */
    protected abstract List<Object> convertTools(List<JsonObject> tools);
    
    /**
     * Parse tool calls from provider response
     * @param response Provider response
     * @return List of ToolCall objects
     */
    protected abstract List<ToolCall> parseToolCalls(Object response);
    
    /**
     * Execute HTTP request with retry logic
     * @param request HTTP request
     * @return Response or null if failed
     */
    protected abstract Object executeWithRetry(Object request);
}