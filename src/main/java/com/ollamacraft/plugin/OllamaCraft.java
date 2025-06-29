package com.ollamacraft.plugin;

import com.ollamacraft.plugin.config.AIConfiguration;
import com.ollamacraft.plugin.provider.AIProviderFactory;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * OllamaCraft main plugin class
 * Integrates AI models with Minecraft via chat interface with intelligent detection
 */
public class OllamaCraft extends JavaPlugin {
    
    private static OllamaCraft instance;
    private AIService aiService; // Legacy service
    private AIServiceV2 aiServiceV2; // Modern multi-provider service
    private boolean useModernArchitecture;
    
    // New multi-provider architecture components
    private AIConfiguration aiConfiguration;
    private AIProviderFactory providerFactory;
    private ResponseDetectionService detectionService;
    private ChatListener chatListener;
    
    @Override
    public void onEnable() {
        // Store instance for static access
        instance = this;
        
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize services
        initializeServices();
        
        // Register commands
        registerCommands();
        
        // Register event listeners
        registerEventListeners();
        
        // Schedule startup integration test
        scheduleStartupTest();
        
        getLogger().info("OllamaCraft has been enabled!");
    }
    
    @Override
    public void onDisable() {
        // Clean up resources
        getLogger().info("Shutting down OllamaCraft services...");
        
        // Shutdown detection service
        if (detectionService != null) {
            detectionService.shutdown();
        }
        
        // Shutdown provider factory
        if (providerFactory != null) {
            providerFactory.shutdown();
        }
        
        // Shutdown AI services
        if (useModernArchitecture && aiServiceV2 != null) {
            getLogger().info("Shutting down modern AI service...");
            aiServiceV2.shutdown();
        } else if (aiService != null) {
            getLogger().info("Shutting down legacy AI service...");
            // Stop MCP bridge if running
            if (aiService.getMcpService() != null && aiService.getMcpService().isRunning()) {
                getLogger().info("Stopping MCP bridge...");
                aiService.getMcpService().stopMCPBridge().join();
            }
            
            aiService.shutdown();
        }
        
        getLogger().info("OllamaCraft has been disabled!");
    }
    
    /**
     * Initialize plugin services
     */
    private void initializeServices() {
        try {
            // Initialize configuration
            aiConfiguration = new AIConfiguration(getConfig(), getLogger());
            
            // Validate configuration
            String validationError = aiConfiguration.validateConfiguration();
            if (validationError != null) {
                getLogger().warning("Configuration validation failed: " + validationError);
                getLogger().info("Using default configuration values...");
            }
            
            // Initialize provider factory
            providerFactory = new AIProviderFactory(getLogger());
            
            // Determine which AI architecture to use
            useModernArchitecture = aiConfiguration.getConfig().getBoolean("ai.use-modern-architecture", true);
            
            // Initialize detection service
            initializeDetectionService();
            
            // Initialize appropriate AI service
            initializeAIServices();
            
            getLogger().info("All services initialized successfully");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize services: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initialize the intelligent response detection service
     */
    private void initializeDetectionService() {
        try {
            if (aiConfiguration.isIntelligentDetection()) {
                detectionService = new ResponseDetectionService(aiConfiguration, providerFactory, getLogger());
                getLogger().info("Intelligent response detection service initialized");
            } else {
                getLogger().info("Intelligent detection disabled, using traditional prefix detection only");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to initialize detection service: " + e.getMessage() + 
                              ", falling back to traditional detection");
        }
    }
    
    /**
     * Initialize AI services based on configuration
     */
    private void initializeAIServices() {
        try {
            if (useModernArchitecture) {
                getLogger().info("Initializing modern multi-provider AI service (AIServiceV2)");
                aiServiceV2 = new AIServiceV2(this);
                
                // Add deprecation notice for users migrating from legacy
                if (aiConfiguration.getProvider().equals("ollama") && 
                    aiConfiguration.getConfig().getBoolean("mcp.enabled", false)) {
                    getLogger().info("Note: Modern architecture provides better multi-provider and MCP integration");
                }
            } else {
                getLogger().warning("========================================");
                getLogger().warning("DEPRECATION WARNING: Using legacy AI service");
                getLogger().warning("========================================");
                getLogger().warning("The legacy AIService is DEPRECATED and will be removed in a future version.");
                getLogger().warning("Legacy limitations:");
                getLogger().warning("- Only supports Ollama provider");
                getLogger().warning("- No intelligent response detection");
                getLogger().warning("- Limited tool calling capabilities");
                getLogger().warning("- No provider switching at runtime");
                getLogger().warning("");
                getLogger().warning("RECOMMENDED ACTION:");
                getLogger().warning("Set 'ai.use-modern-architecture: true' in config.yml to enable:");
                getLogger().warning("- Multi-provider support (Ollama, Claude, OpenAI)");
                getLogger().warning("- Intelligent response detection");
                getLogger().warning("- Enhanced tool calling");
                getLogger().warning("- Runtime provider switching");
                getLogger().warning("========================================");
                aiService = new AIService(this);
            }
        } catch (Exception e) {
            getLogger().severe("Failed to initialize AI services: " + e.getMessage());
            // Try fallback to legacy service
            if (useModernArchitecture && aiService == null) {
                getLogger().warning("Falling back to legacy AI service due to initialization failure");
                try {
                    aiService = new AIService(this);
                    useModernArchitecture = false;
                } catch (Exception fallbackError) {
                    getLogger().severe("Fallback to legacy service also failed: " + fallbackError.getMessage());
                    throw e;
                }
            }
        }
    }
    
    /**
     * Register plugin commands
     */
    private void registerCommands() {
        // Register AI command
        getCommand("ai").setExecutor(new AICommand(this));
        
        // Register AI config command
        getCommand("aiconfig").setExecutor(new AIConfigCommand(this));
    }
    
    /**
     * Register event listeners
     */
    private void registerEventListeners() {
        // Register chat listener and inject detection service
        chatListener = new ChatListener(this);
        if (detectionService != null) {
            chatListener.setDetectionService(detectionService);
        }
        getServer().getPluginManager().registerEvents(chatListener, this);
    }
    
    /**
     * Schedule the startup integration test
     */
    private void scheduleStartupTest() {
        // Check if startup test is enabled
        boolean startupTestEnabled = getConfig().getBoolean("startup-test.enabled", true);
        if (!startupTestEnabled) {
            getLogger().info("Startup integration test is disabled");
            return;
        }
        
        // Get delay from config
        int delaySeconds = getConfig().getInt("startup-test.delay-seconds", 5);
        long delayTicks = delaySeconds * 20L; // Convert seconds to ticks (20 ticks = 1 second)
        
        getLogger().info("Scheduling AI startup integration test in " + delaySeconds + " seconds...");
        
        // Schedule the test to run after server fully starts
        getServer().getScheduler().runTaskLater(this, () -> {
            if (useModernArchitecture && aiServiceV2 != null) {
                aiServiceV2.performStartupTest();
            } else if (!useModernArchitecture && aiService != null) {
                aiService.performStartupTest();
            } else {
                getLogger().warning("AI service not available for startup test");
            }
        }, delayTicks);
    }
    
    /**
     * Get the AI service instance (legacy compatibility)
     * @return AIService instance
     */
    public AIService getAIService() {
        return aiService;
    }
    
    /**
     * Get the modern AI service instance
     * @return AIServiceV2 instance
     */
    public AIServiceV2 getAIServiceV2() {
        return aiServiceV2;
    }
    
    /**
     * Get the active AI service type
     * @return "modern" or "legacy"
     */
    public String getActiveServiceType() {
        if (useModernArchitecture && aiServiceV2 != null) {
            return "modern";
        } else if (aiService != null) {
            return "legacy";
        }
        return "none";
    }
    
    /**
     * Check if using modern architecture
     * @return true if using AIServiceV2
     */
    public boolean isUsingModernArchitecture() {
        return useModernArchitecture && aiServiceV2 != null;
    }
    
    /**
     * Get the AI configuration
     * @return AIConfiguration instance
     */
    public AIConfiguration getAIConfiguration() {
        return aiConfiguration;
    }
    
    /**
     * Get the provider factory
     * @return AIProviderFactory instance
     */
    public AIProviderFactory getProviderFactory() {
        return providerFactory;
    }
    
    /**
     * Get the response detection service
     * @return ResponseDetectionService instance
     */
    public ResponseDetectionService getDetectionService() {
        return detectionService;
    }
    
    /**
     * Get the plugin instance
     * @return OllamaCraft instance
     */
    public static OllamaCraft getInstance() {
        return instance;
    }
}