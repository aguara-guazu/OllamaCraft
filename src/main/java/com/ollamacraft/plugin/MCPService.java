package com.ollamacraft.plugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service to manage the integrated MCP bridge
 */
public class MCPService {
    
    private final OllamaCraft plugin;
    
    private boolean enabled;
    private boolean autoStart;
    private String serverUrl;
    private String apiKey;
    private boolean debug;
    private String endpoint;
    private int timeoutMs;
    private int retries;
    private int retryDelayMs;
    
    // Integrated bridge
    private MCPBridge mcpBridge;
    private boolean isRunning;
    
    /**
     * Constructor for MCPService
     * @param plugin The OllamaCraft plugin instance
     */
    public MCPService(OllamaCraft plugin) {
        this.plugin = plugin;
        this.enabled = false;
        this.isRunning = false;
    }
    
    /**
     * Initialize the MCP service from configuration
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        
        this.enabled = config.getBoolean("mcp.enabled", true);
        this.autoStart = config.getBoolean("mcp.client.auto-start", true);
        this.serverUrl = config.getString("mcp.server.url", "http://localhost:25575");
        this.apiKey = config.getString("mcp.server.api-key", "");
        
        // MCP bridge configuration
        this.debug = config.getBoolean("mcp.bridge.debug", false);
        this.endpoint = config.getString("mcp.bridge.endpoint", "/mcp");
        this.timeoutMs = config.getInt("mcp.bridge.timeout-ms", 30000);
        this.retries = config.getInt("mcp.bridge.retries", 3);
        this.retryDelayMs = config.getInt("mcp.bridge.retry-delay-ms", 1000);
        
        if (enabled && (apiKey == null || apiKey.isEmpty())) {
            plugin.getLogger().warning("MCP integration is enabled but API key is not set. MCP bridge will not be started.");
            enabled = false;
            return;
        }
        
        if (enabled && autoStart) {
            startMCPBridge();
        }
    }
    
    /**
     * Start the integrated MCP bridge
     */
    public CompletableFuture<Boolean> startMCPBridge() {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled) {
                plugin.getLogger().info("MCP integration is disabled, not starting MCP bridge");
                return false;
            }
            
            if (isRunning) {
                plugin.getLogger().info("MCP bridge is already running");
                return true;
            }
            
            try {
                plugin.getLogger().info("Starting integrated MCP bridge...");
                
                MCPBridge.MCPBridgeOptions options = new MCPBridge.MCPBridgeOptions(serverUrl, apiKey)
                    .endpoint(endpoint)
                    .timeout(timeoutMs)
                    .retries(retries)
                    .retryDelay(retryDelayMs)
                    .debug(debug)
                    .logger(plugin.getLogger());
                
                mcpBridge = new MCPBridge(options);
                mcpBridge.start().join();
                
                isRunning = true;
                plugin.getLogger().info("Integrated MCP bridge started successfully");
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error starting integrated MCP bridge", e);
                isRunning = false;
                return false;
            }
        });
    }
    
    /**
     * Stop the integrated MCP bridge
     */
    public CompletableFuture<Void> stopMCPBridge() {
        return CompletableFuture.runAsync(() -> {
            if (!isRunning) {
                plugin.getLogger().info("MCP bridge is not running");
                return;
            }
            
            try {
                plugin.getLogger().info("Stopping integrated MCP bridge...");
                
                if (mcpBridge != null) {
                    mcpBridge.stop().join();
                    mcpBridge = null;
                }
                
                isRunning = false;
                plugin.getLogger().info("Integrated MCP bridge stopped");
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error stopping integrated MCP bridge", e);
                isRunning = false;
            }
        });
    }
    
    /**
     * Restart the MCP bridge
     */
    public CompletableFuture<Boolean> restartMCPBridge() {
        return stopMCPBridge().thenCompose(v -> startMCPBridge());
    }
    
    /**
     * Check if the MCP bridge is running
     * @return True if the MCP bridge is running
     */
    public boolean isRunning() {
        return isRunning && mcpBridge != null && mcpBridge.isRunning();
    }
    
    /**
     * Check if MCP integration is enabled
     * @return True if MCP integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Enable or disable MCP integration
     * @param enabled True to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && isRunning) {
            stopMCPBridge();
        }
    }
    
    /**
     * Get the current server URL
     * @return The server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }
    
    /**
     * Get the current API key (masked for security)
     * @return The masked API key
     */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            return "Not set";
        }
        return "*".repeat(Math.min(apiKey.length(), 8));
    }
    
    /**
     * Get the actual API key (for internal use only)
     * @return The actual API key
     */
    public String getApiKey() {
        return apiKey;
    }
    
}