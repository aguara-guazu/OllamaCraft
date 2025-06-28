package com.ollamacraft.plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Service to manage the MCP bridge - supports both integrated Java bridge and external NPX client
 */
public class MCPService {
    
    private final OllamaCraft plugin;
    
    private boolean enabled;
    private boolean autoStart;
    private String serverUrl;
    private String apiKey;
    private String npxPath;
    private List<String> args;
    private boolean useIntegratedBridge;
    private boolean debug;
    private String endpoint;
    private int timeoutMs;
    private int retries;
    private int retryDelayMs;
    
    // NPX process (legacy mode)
    private Process mcpProcess;
    
    // Integrated bridge (new mode)
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
        this.npxPath = config.getString("mcp.client.npx-path", "npx");
        this.args = config.getStringList("mcp.client.args");
        
        // New integrated bridge configuration
        this.useIntegratedBridge = config.getBoolean("mcp.bridge.use-integrated", true);
        this.debug = config.getBoolean("mcp.bridge.debug", false);
        this.endpoint = config.getString("mcp.bridge.endpoint", "/mcp");
        this.timeoutMs = config.getInt("mcp.bridge.timeout-ms", 30000);
        this.retries = config.getInt("mcp.bridge.retries", 3);
        this.retryDelayMs = config.getInt("mcp.bridge.retry-delay-ms", 1000);
        
        if (enabled && (apiKey == null || apiKey.isEmpty())) {
            plugin.getLogger().warning("MCP integration is enabled but API key is not set. MCP server will not be started.");
            enabled = false;
            return;
        }
        
        if (enabled && autoStart) {
            startMCPServer();
        }
    }
    
    /**
     * Start the MCP server (either integrated bridge or NPX process)
     */
    public CompletableFuture<Boolean> startMCPServer() {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled) {
                plugin.getLogger().info("MCP integration is disabled, not starting MCP server");
                return false;
            }
            
            if (isRunning) {
                plugin.getLogger().info("MCP server is already running");
                return true;
            }
            
            if (useIntegratedBridge) {
                return startIntegratedBridge();
            } else {
                return startNPXProcess();
            }
        });
    }
    
    /**
     * Start the integrated Java MCP bridge
     */
    private boolean startIntegratedBridge() {
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
    }
    
    /**
     * Start the NPX process (legacy mode)
     */
    private boolean startNPXProcess() {
        try {
            // Build command arguments
            List<String> command = new ArrayList<>();
            command.add(npxPath);
            
            // Add configured args (like -y)
            if (args != null && !args.isEmpty()) {
                command.addAll(args);
            }
            
            // Add minecraft-claude-client package and arguments
            command.add("minecraft-claude-client");
            command.add("--server");
            command.add(serverUrl);
            command.add("--api-key");
            command.add(apiKey);
            
            plugin.getLogger().info("Starting NPX MCP client with command: " + String.join(" ", command));
            
            // Start the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            mcpProcess = processBuilder.start();
            isRunning = true;
            
            // Monitor the process output in a separate thread
            Thread outputMonitor = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(mcpProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && isRunning) {
                        plugin.getLogger().info("[MCP] " + line);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        plugin.getLogger().log(Level.WARNING, "Error reading MCP server output", e);
                    }
                }
            });
            outputMonitor.setDaemon(true);
            outputMonitor.start();
            
            // Monitor the process lifecycle
            Thread processMonitor = new Thread(() -> {
                try {
                    int exitCode = mcpProcess.waitFor();
                    isRunning = false;
                    if (exitCode != 0) {
                        plugin.getLogger().warning("MCP server process exited with code: " + exitCode);
                    } else {
                        plugin.getLogger().info("MCP server process exited normally");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().info("MCP server process monitor interrupted");
                }
            });
            processMonitor.setDaemon(true);
            processMonitor.start();
            
            // Give the process a moment to start
            Thread.sleep(2000);
            
            if (mcpProcess.isAlive()) {
                plugin.getLogger().info("NPX MCP client started successfully");
                return true;
            } else {
                plugin.getLogger().warning("NPX MCP client failed to start");
                isRunning = false;
                return false;
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error starting NPX MCP client", e);
            isRunning = false;
            return false;
        }
    }
    
    /**
     * Stop the MCP server (either integrated bridge or NPX process)
     */
    public CompletableFuture<Void> stopMCPServer() {
        return CompletableFuture.runAsync(() -> {
            if (!isRunning) {
                plugin.getLogger().info("MCP server is not running");
                return;
            }
            
            if (useIntegratedBridge && mcpBridge != null) {
                stopIntegratedBridge();
            } else if (mcpProcess != null) {
                stopNPXProcess();
            }
        });
    }
    
    /**
     * Stop the integrated Java MCP bridge
     */
    private void stopIntegratedBridge() {
        try {
            plugin.getLogger().info("Stopping integrated MCP bridge...");
            
            mcpBridge.stop().join();
            mcpBridge = null;
            isRunning = false;
            
            plugin.getLogger().info("Integrated MCP bridge stopped");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error stopping integrated MCP bridge", e);
            isRunning = false;
        }
    }
    
    /**
     * Stop the NPX process (legacy mode)
     */
    private void stopNPXProcess() {
        try {
            plugin.getLogger().info("Stopping NPX MCP client...");
            
            // Try graceful shutdown first
            mcpProcess.destroy();
            
            // Wait for graceful shutdown
            boolean terminated = mcpProcess.waitFor(10, TimeUnit.SECONDS);
            
            if (!terminated) {
                plugin.getLogger().warning("NPX MCP client did not terminate gracefully, forcing shutdown...");
                mcpProcess.destroyForcibly();
                mcpProcess.waitFor(5, TimeUnit.SECONDS);
            }
            
            isRunning = false;
            plugin.getLogger().info("NPX MCP client stopped");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error stopping NPX MCP client", e);
            isRunning = false;
        }
    }
    
    /**
     * Restart the MCP server
     */
    public CompletableFuture<Boolean> restartMCPServer() {
        return stopMCPServer().thenCompose(v -> startMCPServer());
    }
    
    /**
     * Check if the MCP server is running
     * @return True if the MCP server is running
     */
    public boolean isRunning() {
        if (useIntegratedBridge) {
            return isRunning && mcpBridge != null && mcpBridge.isRunning();
        } else {
            return isRunning && mcpProcess != null && mcpProcess.isAlive();
        }
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
            stopMCPServer();
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
     * Check if using integrated bridge
     * @return True if using integrated bridge, false if using NPX
     */
    public boolean isUsingIntegratedBridge() {
        return useIntegratedBridge;
    }
    
    /**
     * Get the actual API key (for internal use only)
     * @return The actual API key
     */
    public String getApiKey() {
        return apiKey;
    }
    
}