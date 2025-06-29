package com.ollamacraft.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * OllamaCraft main plugin class
 * Integrates Ollama AI models with Minecraft via chat interface
 */
public class OllamaCraft extends JavaPlugin {
    
    private static OllamaCraft instance;
    private AIService aiService;
    
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
        // Initialize AI service
        aiService = new AIService(this);
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
        // Register chat listener
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
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
     * Get the plugin instance
     * @return OllamaCraft instance
     */
    public static OllamaCraft getInstance() {
        return instance;
    }
}