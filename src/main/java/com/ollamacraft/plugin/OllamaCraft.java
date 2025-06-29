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
    private AIService aiService;
    
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
        
        // Shutdown AI service
        if (aiService != null) {
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
            
            // Initialize detection service
            initializeDetectionService();
            
            // Initialize AI service (legacy for backward compatibility)
            aiService = new AIService(this);
            
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
            if (aiService != null) {
                aiService.performStartupTest();
            } else {
                getLogger().warning("AI service not available for startup test");
            }
        }, delayTicks);
    }
    
    /**
     * Get the AI service instance
     * @return AIService instance
     */
    public AIService getAIService() {
        return aiService;
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