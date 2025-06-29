package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;
import com.ollamacraft.plugin.model.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for AI providers (Ollama, Claude, OpenAI, etc.)
 * Provides a unified way to interact with different AI services
 */
public interface AIProvider {
    
    /**
     * Send a chat message to the AI provider
     * @param messages List of chat messages (conversation history)
     * @param systemPrompt System prompt to set context
     * @return CompletableFuture with AI response
     */
    CompletableFuture<AIResponse> chat(List<ChatMessage> messages, String systemPrompt);
    
    /**
     * Send a chat message with tool calling support
     * @param messages List of chat messages
     * @param systemPrompt System prompt
     * @param tools Available tools for the AI to use
     * @return CompletableFuture with AI response
     */
    CompletableFuture<AIResponse> chatWithTools(List<ChatMessage> messages, String systemPrompt, List<JsonObject> tools);
    
    /**
     * Get the provider name (ollama, claude, openai)
     * @return Provider identifier
     */
    String getProviderName();
    
    /**
     * Get available models for this provider
     * @return List of model names
     */
    List<String> getSupportedModels();
    
    /**
     * Check if this provider supports native tool calling
     * @return true if native tool calling is supported
     */
    boolean supportsNativeTools();
    
    /**
     * Configure the provider with settings
     * @param config Configuration map
     */
    void configure(Map<String, Object> config);
    
    /**
     * Test if the provider is available and working
     * @return CompletableFuture with test result
     */
    CompletableFuture<Boolean> testConnection();
    
    /**
     * Get the current model being used
     * @return Model name
     */
    String getCurrentModel();
    
    /**
     * Set the model to use
     * @param model Model name
     */
    void setModel(String model);
    
    /**
     * Get temperature setting
     * @return Temperature value
     */
    double getTemperature();
    
    /**
     * Set temperature setting
     * @param temperature Temperature value (0.0-1.0)
     */
    void setTemperature(double temperature);
    
    /**
     * Cleanup resources when shutting down
     */
    void shutdown();
}