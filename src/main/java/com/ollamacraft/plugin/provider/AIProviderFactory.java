package com.ollamacraft.plugin.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Factory for creating and managing AI provider instances
 * Supports Ollama, Claude, and OpenAI providers
 */
public class AIProviderFactory {
    
    private final Logger logger;
    private final Map<String, AIProvider> providerCache;
    
    public AIProviderFactory(Logger logger) {
        this.logger = logger;
        this.providerCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Create or get cached provider instance
     * @param providerName Name of the provider (ollama, claude, openai)
     * @param config Configuration map for the provider
     * @return Configured AIProvider instance
     * @throws IllegalArgumentException if provider name is not supported
     */
    public AIProvider createProvider(String providerName, Map<String, Object> config) {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }
        
        String normalizedName = providerName.toLowerCase().trim();
        
        // Check cache first
        String cacheKey = normalizedName + "_" + config.hashCode();
        AIProvider cachedProvider = providerCache.get(cacheKey);
        if (cachedProvider != null) {
            logger.info("Using cached " + normalizedName + " provider");
            return cachedProvider;
        }
        
        // Create new provider instance
        AIProvider provider = createNewProvider(normalizedName);
        
        // Configure the provider
        provider.configure(config);
        
        // Cache the provider
        providerCache.put(cacheKey, provider);
        
        logger.info("Created and configured " + normalizedName + " provider");
        return provider;
    }
    
    /**
     * Create a new provider instance without caching
     * @param providerName Name of the provider
     * @return New AIProvider instance
     * @throws IllegalArgumentException if provider is not supported
     */
    private AIProvider createNewProvider(String providerName) {
        switch (providerName) {
            case "ollama":
                return new OllamaProvider(logger);
            case "claude":
                return new ClaudeProvider(logger);
            case "openai":
                return new OpenAIProvider(logger);
            default:
                throw new IllegalArgumentException("Unsupported AI provider: " + providerName + 
                    ". Supported providers: ollama, claude, openai");
        }
    }
    
    /**
     * Get all supported provider names
     * @return Array of supported provider names
     */
    public static String[] getSupportedProviders() {
        return new String[]{"ollama", "claude", "openai"};
    }
    
    /**
     * Check if a provider name is supported
     * @param providerName Provider name to check
     * @return true if supported, false otherwise
     */
    public static boolean isProviderSupported(String providerName) {
        if (providerName == null) return false;
        String normalized = providerName.toLowerCase().trim();
        for (String supported : getSupportedProviders()) {
            if (supported.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Create a provider with minimal configuration for testing
     * @param providerName Provider name
     * @return Unconfigured provider instance
     */
    public AIProvider createTestProvider(String providerName) {
        return createNewProvider(providerName.toLowerCase().trim());
    }
    
    /**
     * Clear the provider cache
     * Useful when configuration changes significantly
     */
    public void clearCache() {
        logger.info("Clearing AI provider cache");
        
        // Shutdown any cached providers
        for (AIProvider provider : providerCache.values()) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                logger.warning("Error shutting down cached provider: " + e.getMessage());
            }
        }
        
        providerCache.clear();
    }
    
    /**
     * Get provider information including supported models
     * @param providerName Provider name
     * @return Provider information string
     */
    public String getProviderInfo(String providerName) {
        try {
            AIProvider provider = createTestProvider(providerName);
            StringBuilder info = new StringBuilder();
            info.append("Provider: ").append(provider.getProviderName()).append("\n");
            info.append("Supports Native Tools: ").append(provider.supportsNativeTools()).append("\n");
            info.append("Supported Models:\n");
            
            for (String model : provider.getSupportedModels()) {
                info.append("  - ").append(model).append("\n");
            }
            
            return info.toString();
            
        } catch (Exception e) {
            return "Error getting provider info: " + e.getMessage();
        }
    }
    
    /**
     * Validate provider configuration
     * @param providerName Provider name
     * @param config Configuration to validate
     * @return Validation result message
     */
    public String validateProviderConfig(String providerName, Map<String, Object> config) {
        try {
            if (!isProviderSupported(providerName)) {
                return "Unsupported provider: " + providerName;
            }
            
            AIProvider provider = createTestProvider(providerName);
            provider.configure(config);
            
            // Try to test connection if possible
            try {
                Boolean connectionTest = provider.testConnection().get();
                if (connectionTest) {
                    return "Configuration valid and connection successful";
                } else {
                    return "Configuration valid but connection failed";
                }
            } catch (Exception e) {
                return "Configuration valid but connection test failed: " + e.getMessage();
            }
            
        } catch (Exception e) {
            return "Configuration invalid: " + e.getMessage();
        }
    }
    
    /**
     * Get recommended configuration template for a provider
     * @param providerName Provider name
     * @return Configuration template as string
     */
    public String getConfigTemplate(String providerName) {
        switch (providerName.toLowerCase().trim()) {
            case "ollama":
                return "ollama:\n" +
                       "  model: \"llama3.1\"\n" +
                       "  base-url: \"http://localhost:11434/api\"\n" +
                       "  temperature: 0.7\n" +
                       "  timeout-seconds: 60\n" +
                       "  max-retries: 3";
                       
            case "claude":
                return "claude:\n" +
                       "  model: \"claude-3-5-sonnet-20241022\"\n" +
                       "  api-key: \"your-claude-api-key\"\n" +
                       "  base-url: \"https://api.anthropic.com/v1\"\n" +
                       "  temperature: 0.7\n" +
                       "  timeout-seconds: 60\n" +
                       "  max-retries: 3";
                       
            case "openai":
                return "openai:\n" +
                       "  model: \"gpt-4o\"\n" +
                       "  api-key: \"your-openai-api-key\"\n" +
                       "  base-url: \"https://api.openai.com/v1\"\n" +
                       "  organization: \"your-org-id\"  # Optional\n" +
                       "  temperature: 0.7\n" +
                       "  timeout-seconds: 60\n" +
                       "  max-retries: 3";
                       
            default:
                return "Unsupported provider: " + providerName;
        }
    }
    
    /**
     * Shutdown all cached providers and clear cache
     */
    public void shutdown() {
        logger.info("Shutting down AI provider factory");
        clearCache();
    }
    
    /**
     * Get cache statistics
     * @return Cache statistics string
     */
    public String getCacheStats() {
        return "Cached providers: " + providerCache.size();
    }
}