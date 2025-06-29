package com.ollamacraft.plugin.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for creating tool converters for different AI providers
 */
public class ToolConverterFactory {
    
    private final Logger logger;
    private final Map<String, ToolConverter> converterCache;
    
    public ToolConverterFactory(Logger logger) {
        this.logger = logger;
        this.converterCache = new HashMap<>();
    }
    
    /**
     * Get tool converter for a specific provider
     * @param providerName Name of the provider
     * @return ToolConverter instance
     * @throws IllegalArgumentException if provider is not supported
     */
    public ToolConverter getConverter(String providerName) {
        if (providerName == null) {
            throw new IllegalArgumentException("Provider name cannot be null");
        }
        
        String normalizedName = providerName.toLowerCase().trim();
        
        // Check cache first
        ToolConverter cached = converterCache.get(normalizedName);
        if (cached != null) {
            return cached;
        }
        
        // Create new converter
        ToolConverter converter = createConverter(normalizedName);
        converterCache.put(normalizedName, converter);
        
        return converter;
    }
    
    /**
     * Create a new tool converter instance
     * @param providerName Provider name (normalized)
     * @return ToolConverter instance
     */
    private ToolConverter createConverter(String providerName) {
        switch (providerName) {
            case "ollama":
                return new OllamaToolConverter(logger);
            case "claude":
                return new ClaudeToolConverter(logger);
            case "openai":
                return new OpenAIToolConverter(logger);
            default:
                throw new IllegalArgumentException("Unsupported provider for tool conversion: " + providerName);
        }
    }
    
    /**
     * Check if a provider has tool conversion support
     * @param providerName Provider name
     * @return true if supported
     */
    public boolean isProviderSupported(String providerName) {
        if (providerName == null) return false;
        
        String normalized = providerName.toLowerCase().trim();
        return "ollama".equals(normalized) || 
               "claude".equals(normalized) || 
               "openai".equals(normalized);
    }
    
    /**
     * Get all supported provider names
     * @return Array of supported provider names
     */
    public String[] getSupportedProviders() {
        return new String[]{"ollama", "claude", "openai"};
    }
    
    /**
     * Clear the converter cache
     */
    public void clearCache() {
        converterCache.clear();
        logger.info("Tool converter cache cleared");
    }
    
    /**
     * Get cache statistics
     * @return Cache stats string
     */
    public String getCacheStats() {
        return "Cached converters: " + converterCache.size();
    }
}