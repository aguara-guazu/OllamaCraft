package com.ollamacraft.plugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration manager for multi-provider AI settings
 * Handles configuration loading and validation for all AI providers
 */
public class AIConfiguration {
    
    private final Logger logger;
    private final FileConfiguration config;
    
    // Main AI settings
    private String primaryProvider;
    private String agentName;
    private String systemPrompt;
    private double temperature;
    private int maxContextLength;
    
    // Provider configurations
    private Map<String, Map<String, Object>> providerConfigs;
    
    // Chat settings
    private boolean monitorAllChat;
    private String triggerPrefix;
    private String responseFormat;
    
    // Detection settings
    private boolean intelligentDetection;
    private boolean fallbackToPrefix;
    private int detectionTimeoutSeconds;
    private String detectionProvider;
    private double confidenceThreshold;
    private boolean cacheDecisions;
    private int cacheDurationMinutes;
    
    // MCP settings
    private boolean mcpEnabled;
    private Map<String, Object> mcpConfig;
    
    public AIConfiguration(FileConfiguration config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.providerConfigs = new HashMap<>();
        loadConfiguration();
    }
    
    /**
     * Load configuration from file
     */
    public void loadConfiguration() {
        try {
            // Load main AI settings
            loadMainAISettings();
            
            // Load provider configurations
            loadProviderConfigurations();
            
            // Load chat settings
            loadChatSettings();
            
            // Load MCP settings
            loadMCPSettings();
            
            logger.info("AI configuration loaded successfully - Provider: " + primaryProvider + 
                       ", Agent: " + agentName);
            
        } catch (Exception e) {
            logger.severe("Failed to load AI configuration: " + e.getMessage());
            setDefaultConfiguration();
        }
    }
    
    /**
     * Load main AI settings
     */
    private void loadMainAISettings() {
        primaryProvider = config.getString("ai.provider", "ollama");
        agentName = config.getString("ai.agent-name", "Steve");
        systemPrompt = config.getString("ai.system-prompt", 
            "You are {agent_name}, a helpful assistant in a Minecraft world.");
        temperature = config.getDouble("ai.temperature", 0.7);
        maxContextLength = config.getInt("ai.max-context-length", 50);
        
        // Replace agent name placeholder in system prompt
        systemPrompt = systemPrompt.replace("{agent_name}", agentName);
    }
    
    /**
     * Load provider configurations
     */
    private void loadProviderConfigurations() {
        // Load Ollama configuration
        Map<String, Object> ollamaConfig = new HashMap<>();
        ConfigurationSection ollamaSection = config.getConfigurationSection("ollama");
        if (ollamaSection != null) {
            ollamaConfig.put("base-url", ollamaSection.getString("base-url", "http://localhost:11434/api"));
            ollamaConfig.put("model", ollamaSection.getString("model", "llama3.1"));
            ollamaConfig.put("timeout-seconds", ollamaSection.getInt("timeout-seconds", 60));
            ollamaConfig.put("max-retries", ollamaSection.getInt("max-retries", 3));
            ollamaConfig.put("temperature", temperature);
        }
        providerConfigs.put("ollama", ollamaConfig);
        
        // Load Claude configuration
        Map<String, Object> claudeConfig = new HashMap<>();
        ConfigurationSection claudeSection = config.getConfigurationSection("claude");
        if (claudeSection != null) {
            claudeConfig.put("api-key", claudeSection.getString("api-key", ""));
            claudeConfig.put("base-url", claudeSection.getString("base-url", "https://api.anthropic.com/v1"));
            claudeConfig.put("model", claudeSection.getString("model", "claude-3-5-sonnet-20241022"));
            claudeConfig.put("timeout-seconds", claudeSection.getInt("timeout-seconds", 60));
            claudeConfig.put("max-retries", claudeSection.getInt("max-retries", 3));
            claudeConfig.put("temperature", temperature);
        }
        providerConfigs.put("claude", claudeConfig);
        
        // Load OpenAI configuration
        Map<String, Object> openaiConfig = new HashMap<>();
        ConfigurationSection openaiSection = config.getConfigurationSection("openai");
        if (openaiSection != null) {
            openaiConfig.put("api-key", openaiSection.getString("api-key", ""));
            openaiConfig.put("base-url", openaiSection.getString("base-url", "https://api.openai.com/v1"));
            openaiConfig.put("model", openaiSection.getString("model", "gpt-4o"));
            openaiConfig.put("organization", openaiSection.getString("organization", ""));
            openaiConfig.put("timeout-seconds", openaiSection.getInt("timeout-seconds", 60));
            openaiConfig.put("max-retries", openaiSection.getInt("max-retries", 3));
            openaiConfig.put("temperature", temperature);
        }
        providerConfigs.put("openai", openaiConfig);
    }
    
    /**
     * Load chat settings
     */
    private void loadChatSettings() {
        monitorAllChat = config.getBoolean("chat.monitor-all-chat", false);
        triggerPrefix = config.getString("chat.trigger-prefix", agentName + ", ");
        responseFormat = config.getString("chat.response-format", "[{agent_name}] &a%message%");
        
        // Replace agent name placeholder in formats
        responseFormat = responseFormat.replace("{agent_name}", agentName);
        
        // Load detection settings
        intelligentDetection = config.getBoolean("chat.detection.intelligent-detection", false);
        fallbackToPrefix = config.getBoolean("chat.detection.fallback-to-prefix", true);
        detectionTimeoutSeconds = config.getInt("chat.detection.detection-timeout-seconds", 3);
        detectionProvider = config.getString("chat.detection.detection-provider", "ollama");
        confidenceThreshold = config.getDouble("chat.detection.confidence-threshold", 0.6);
        cacheDecisions = config.getBoolean("chat.detection.cache-decisions", true);
        cacheDurationMinutes = config.getInt("chat.detection.cache-duration-minutes", 5);
    }
    
    /**
     * Load MCP settings
     */
    private void loadMCPSettings() {
        mcpEnabled = config.getBoolean("mcp.enabled", false);
        mcpConfig = new HashMap<>();
        
        ConfigurationSection mcpSection = config.getConfigurationSection("mcp");
        if (mcpSection != null) {
            // Convert the entire MCP section to a map
            for (String key : mcpSection.getKeys(true)) {
                mcpConfig.put(key, mcpSection.get(key));
            }
        }
    }
    
    /**
     * Set default configuration when loading fails
     */
    private void setDefaultConfiguration() {
        logger.warning("Using default AI configuration");
        
        primaryProvider = "ollama";
        agentName = "Steve";
        systemPrompt = "You are Steve, a helpful assistant in a Minecraft world.";
        temperature = 0.7;
        maxContextLength = 50;
        
        // Default Ollama config
        Map<String, Object> defaultOllama = new HashMap<>();
        defaultOllama.put("base-url", "http://localhost:11434/api");
        defaultOllama.put("model", "llama3.1");
        defaultOllama.put("timeout-seconds", 60);
        defaultOllama.put("max-retries", 3);
        defaultOllama.put("temperature", 0.7);
        providerConfigs.put("ollama", defaultOllama);
        
        monitorAllChat = false;
        triggerPrefix = "Steve, ";
        responseFormat = "[Steve] &a%message%";
        intelligentDetection = false;
        fallbackToPrefix = true;
        detectionTimeoutSeconds = 5;
        detectionProvider = "ollama";
        confidenceThreshold = 0.6;
        cacheDecisions = true;
        cacheDurationMinutes = 5;
        
        mcpEnabled = false;
        mcpConfig = new HashMap<>();
    }
    
    /**
     * Validate configuration
     * @return Validation error message, or null if valid
     */
    public String validateConfiguration() {
        // Check if primary provider is supported
        if (!isProviderSupported(primaryProvider)) {
            return "Unsupported primary provider: '" + primaryProvider + "'. Supported providers: ollama, claude, openai";
        }
        
        // Validate provider-specific configuration
        Map<String, Object> providerConfig = getProviderConfiguration(primaryProvider);
        if (providerConfig == null || providerConfig.isEmpty()) {
            return "No configuration found for provider: " + primaryProvider + ". Please add provider-specific settings to config.yml";
        }
        
        // Validate primary provider configuration
        String providerValidation = validateProviderConfig(primaryProvider, providerConfig);
        if (providerValidation != null) {
            return providerValidation;
        }
        
        // Validate detection provider if intelligent detection is enabled
        if (intelligentDetection) {
            if (!isProviderSupported(detectionProvider)) {
                return "Unsupported detection provider: '" + detectionProvider + "'. Must be one of: ollama, claude, openai";
            }
            
            // If detection provider is different from primary, validate its config too
            if (!detectionProvider.equals(primaryProvider)) {
                Map<String, Object> detectionConfig = getProviderConfiguration(detectionProvider);
                if (detectionConfig == null || detectionConfig.isEmpty()) {
                    return "Detection provider '" + detectionProvider + "' requires configuration but none found";
                }
                
                String detectionValidation = validateProviderConfig(detectionProvider, detectionConfig);
                if (detectionValidation != null) {
                    return "Detection provider error: " + detectionValidation;
                }
            }
            
            // Validate detection settings
            if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
                return "Confidence threshold must be between 0.0 and 1.0, got: " + confidenceThreshold;
            }
            
            if (detectionTimeoutSeconds < 1 || detectionTimeoutSeconds > 30) {
                return "Detection timeout must be between 1 and 30 seconds, got: " + detectionTimeoutSeconds;
            }
        }
        
        // Validate general settings
        if (temperature < 0.0 || temperature > 1.0) {
            return "Temperature must be between 0.0 and 1.0, got: " + temperature;
        }
        
        if (maxContextLength < 1 || maxContextLength > 1000) {
            return "Max context length must be between 1 and 1000, got: " + maxContextLength;
        }
        
        if (agentName == null || agentName.trim().isEmpty()) {
            return "Agent name cannot be empty";
        }
        
        return null; // Configuration is valid
    }
    
    /**
     * Validate provider-specific configuration
     */
    private String validateProviderConfig(String provider, Map<String, Object> config) {
        switch (provider) {
            case "claude":
                return validateClaudeConfig(config);
            case "openai":
                return validateOpenAIConfig(config);
            case "ollama":
                return validateOllamaConfig(config);
            default:
                return "Unknown provider: " + provider;
        }
    }
    
    /**
     * Validate Claude provider configuration
     */
    private String validateClaudeConfig(Map<String, Object> config) {
        String apiKey = (String) config.get("api-key");
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your-")) {
            return "Claude API key is required. Get one from https://console.anthropic.com/ and set 'claude.api-key' in config.yml";
        }
        
        String model = (String) config.get("model");
        if (model == null || model.trim().isEmpty()) {
            return "Claude model is required. Set 'claude.model' (e.g., 'claude-3-5-sonnet-20241022')";
        }
        
        if (!isValidClaudeModel(model)) {
            return "Invalid Claude model: '" + model + "'. Use a valid Claude model like 'claude-3-5-sonnet-20241022'";
        }
        
        String baseUrl = (String) config.get("base-url");
        if (baseUrl != null && !baseUrl.startsWith("https://")) {
            return "Claude base URL must use HTTPS";
        }
        
        return null;
    }
    
    /**
     * Validate OpenAI provider configuration
     */
    private String validateOpenAIConfig(Map<String, Object> config) {
        String apiKey = (String) config.get("api-key");
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your-")) {
            return "OpenAI API key is required. Get one from https://platform.openai.com/ and set 'openai.api-key' in config.yml";
        }
        
        String model = (String) config.get("model");
        if (model == null || model.trim().isEmpty()) {
            return "OpenAI model is required. Set 'openai.model' (e.g., 'gpt-4o')";
        }
        
        if (!isValidOpenAIModel(model)) {
            return "Invalid OpenAI model: '" + model + "'. Use a valid model like 'gpt-4o' or 'gpt-3.5-turbo'";
        }
        
        String baseUrl = (String) config.get("base-url");
        if (baseUrl != null && !baseUrl.startsWith("https://")) {
            return "OpenAI base URL must use HTTPS";
        }
        
        return null;
    }
    
    /**
     * Validate Ollama provider configuration
     */
    private String validateOllamaConfig(Map<String, Object> config) {
        String baseUrl = (String) config.get("base-url");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "Ollama base URL is required. Set 'ollama.base-url' (e.g., 'http://localhost:11434/api')";
        }
        
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return "Ollama base URL must start with http:// or https://";
        }
        
        String model = (String) config.get("model");
        if (model == null || model.trim().isEmpty()) {
            return "Ollama model is required. Set 'ollama.model' (e.g., 'llama3.1')";
        }
        
        return null;
    }
    
    /**
     * Check if Claude model is valid
     */
    private boolean isValidClaudeModel(String model) {
        return model.startsWith("claude-3-") || model.startsWith("claude-4-") || 
               model.equals("claude-3-opus-20240229") || 
               model.equals("claude-3-sonnet-20240229") ||
               model.equals("claude-3-haiku-20240307") ||
               model.equals("claude-3-5-sonnet-20241022") ||
               model.equals("claude-3-5-haiku-20241022") ||
               model.equals("claude-sonnet-4-20250514") ||
               model.equals("claude-opus-4-20250514");
    }
    
    /**
     * Check if OpenAI model is valid
     */
    private boolean isValidOpenAIModel(String model) {
        return model.startsWith("gpt-") || 
               model.equals("gpt-4o") || 
               model.equals("gpt-4o-mini") ||
               model.equals("gpt-4-turbo") ||
               model.equals("gpt-4") ||
               model.equals("gpt-3.5-turbo") ||
               model.equals("gpt-3.5-turbo-16k");
    }
    
    /**
     * Check if a provider is supported
     */
    private boolean isProviderSupported(String provider) {
        return "ollama".equals(provider) || "claude".equals(provider) || "openai".equals(provider);
    }
    
    // Getters
    public String getPrimaryProvider() { return primaryProvider; }
    public String getProvider() { return primaryProvider; } // Alias for compatibility
    public String getAgentName() { return agentName; }
    public String getSystemPrompt() { return systemPrompt; }
    public double getTemperature() { return temperature; }
    public int getMaxContextLength() { return maxContextLength; }
    public FileConfiguration getConfig() { return config; } // For direct config access
    
    public Map<String, Object> getProviderConfiguration(String provider) {
        return providerConfigs.get(provider);
    }
    
    public boolean isMonitorAllChat() { return monitorAllChat; }
    public String getTriggerPrefix() { return triggerPrefix; }
    public String getResponseFormat() { return responseFormat; }
    
    public boolean isIntelligentDetection() { return intelligentDetection; }
    public boolean isFallbackToPrefix() { return fallbackToPrefix; }
    public int getDetectionTimeoutSeconds() { return detectionTimeoutSeconds; }
    public String getDetectionProvider() { return detectionProvider; }
    public double getConfidenceThreshold() { return confidenceThreshold; }
    public boolean isCacheDecisions() { return cacheDecisions; }
    public int getCacheDurationMinutes() { return cacheDurationMinutes; }
    
    public boolean isMcpEnabled() { return mcpEnabled; }
    public Map<String, Object> getMcpConfig() { return mcpConfig; }
    
    // Setters for runtime configuration changes
    public void setPrimaryProvider(String provider) {
        this.primaryProvider = provider;
        updateConfigFile("ai.provider", provider);
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
        // Update system prompt and response format
        this.systemPrompt = systemPrompt.replace(this.agentName, agentName);
        this.responseFormat = responseFormat.replace(this.agentName, agentName);
        this.triggerPrefix = agentName + ", ";
        updateConfigFile("ai.agent-name", agentName);
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
        // Update all provider configs
        for (Map<String, Object> providerConfig : providerConfigs.values()) {
            providerConfig.put("temperature", temperature);
        }
        updateConfigFile("ai.temperature", temperature);
    }
    
    /**
     * Update configuration file
     */
    private void updateConfigFile(String path, Object value) {
        config.set(path, value);
        // Note: In a real implementation, you'd save the config file here
        logger.info("Updated configuration: " + path + " = " + value);
    }
    
    /**
     * Get configuration summary
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("AI Configuration Summary:\n");
        summary.append("- Primary Provider: ").append(primaryProvider).append("\n");
        summary.append("- Agent Name: ").append(agentName).append("\n");
        summary.append("- Temperature: ").append(temperature).append("\n");
        summary.append("- Max Context: ").append(maxContextLength).append("\n");
        summary.append("- Intelligent Detection: ").append(intelligentDetection).append("\n");
        summary.append("- Detection Provider: ").append(detectionProvider).append("\n");
        summary.append("- Confidence Threshold: ").append(confidenceThreshold).append("\n");
        summary.append("- MCP Enabled: ").append(mcpEnabled).append("\n");
        
        Map<String, Object> primaryConfig = getProviderConfiguration(primaryProvider);
        if (primaryConfig != null) {
            summary.append("- Primary Model: ").append(primaryConfig.get("model")).append("\n");
        }
        
        return summary.toString();
    }
}