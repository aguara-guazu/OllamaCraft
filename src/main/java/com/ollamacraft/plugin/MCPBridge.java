package com.ollamacraft.plugin;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure MCP protocol relay between stdio and HTTP
 */
public class MCPBridge {
    
    private final MCPHttpClient httpClient;
    private final MCPStdioHandler stdioHandler;
    private final boolean debug;
    private final Logger logger;
    private final AtomicBoolean isRunning;
    private final MCPBridgeOptions options;
    
    /**
     * Create a new MCP bridge
     * @param options Configuration options
     */
    public MCPBridge(MCPBridgeOptions options) {
        this.options = options;
        this.debug = options.debug;
        this.logger = options.logger;
        this.isRunning = new AtomicBoolean(false);
        
        // Create HTTP client
        MCPHttpClient.MCPHttpClientOptions httpOptions = new MCPHttpClient.MCPHttpClientOptions(
            options.serverUrl, options.apiKey)
            .endpoint(options.endpoint)
            .timeout(options.timeoutMs)
            .retries(options.retries)
            .retryDelay(options.retryDelayMs)
            .debug(options.debug)
            .logger(options.logger);
        
        this.httpClient = new MCPHttpClient(httpOptions);
        
        // Create stdio handler
        MCPStdioHandler.MCPStdioHandlerOptions stdioOptions = new MCPStdioHandler.MCPStdioHandlerOptions()
            .debug(options.debug)
            .onMessage(this::handleStdinMessage)
            .logger(options.logger);
        
        this.stdioHandler = new MCPStdioHandler(stdioOptions);
        
        log("Bridge initialized for server: " + options.serverUrl + options.endpoint);
    }
    
    /**
     * Log a message if debug mode is enabled
     */
    private void log(String message) {
        if (debug && logger != null) {
            logger.info("[MCPBridge] " + message);
        }
    }
    
    /**
     * Handle a message from stdin
     * @param message Message received from stdin
     */
    private void handleStdinMessage(JsonObject message) {
        CompletableFuture.runAsync(() -> {
            try {
                // Forward the message to the HTTP server as-is, with zero parsing or validation
                log("Relaying message to HTTP server: " + message);
                
                httpClient.sendMessage(message).whenComplete((response, error) -> {
                    if (error != null) {
                        if (logger != null) {
                            logger.log(Level.WARNING, "[MCPBridge] Error relaying message: " + error.getMessage());
                        }
                        
                        // Send error response in JSON-RPC format
                        JsonObject errorResponse = new JsonObject();
                        errorResponse.addProperty("jsonrpc", "2.0");
                        
                        if (message.has("id")) {
                            errorResponse.add("id", message.get("id"));
                        } else {
                            errorResponse.addProperty("id", (String) null);
                        }
                        
                        JsonObject errorObject = new JsonObject();
                        errorObject.addProperty("code", -32000);
                        errorObject.addProperty("message", "Relay error: " + error.getMessage());
                        
                        JsonObject errorData = new JsonObject();
                        errorData.addProperty("type", "relay_error");
                        errorData.addProperty("details", error.toString());
                        errorObject.add("data", errorData);
                        
                        errorResponse.add("error", errorObject);
                        
                        stdioHandler.sendMessage(errorResponse);
                    } else {
                        // Forward the response back to stdout as-is
                        log("Relaying response back to stdout: " + response);
                        stdioHandler.sendMessage(response);
                    }
                });
                
            } catch (Exception error) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "[MCPBridge] Unexpected error in message handler", error);
                }
            }
        });
    }
    
    /**
     * Start the MCP bridge
     * @return CompletableFuture that completes when the bridge is started
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (isRunning.get()) {
                log("Bridge is already running");
                return;
            }
            
            try {
                // Test connection to HTTP server
                httpClient.testConnection().join();
                
                if (logger != null) {
                    logger.info("[MCPBridge] MCP bridge started successfully.");
                    logger.info("[MCPBridge] Proxying MCP messages between:");
                    logger.info("[MCPBridge] - stdin/stdout <--> " + options.serverUrl + options.endpoint);
                }
                
                // Start stdio handler
                stdioHandler.start();
                isRunning.set(true);
                
                log("Bridge is now running and ready to relay messages");
                
            } catch (Exception error) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "[MCPBridge] Failed to start MCP bridge: " + error.getMessage());
                }
                throw new RuntimeException("Failed to start MCP bridge", error);
            }
        });
    }
    
    /**
     * Stop the MCP bridge
     * @return CompletableFuture that completes when the bridge is stopped
     */
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (!isRunning.get()) {
                log("Bridge is not running");
                return;
            }
            
            log("Stopping bridge");
            isRunning.set(false);
            
            try {
                // Stop stdio handler
                stdioHandler.stop();
                
                if (logger != null) {
                    logger.info("[MCPBridge] MCP bridge stopped");
                }
                
            } catch (Exception error) {
                if (logger != null) {
                    logger.log(Level.WARNING, "[MCPBridge] Error stopping bridge", error);
                }
            }
        });
    }
    
    /**
     * Check if the bridge is running
     * @return True if the bridge is running
     */
    public boolean isRunning() {
        return isRunning.get() && stdioHandler.isRunning();
    }
    
    /**
     * Configuration options for MCPBridge
     */
    public static class MCPBridgeOptions {
        public String serverUrl;
        public String apiKey;
        public String endpoint = "/mcp";
        public int timeoutMs = 30000;
        public int retries = 3;
        public int retryDelayMs = 1000;
        public boolean debug = false;
        public Logger logger;
        
        public MCPBridgeOptions(String serverUrl, String apiKey) {
            this.serverUrl = serverUrl;
            this.apiKey = apiKey;
        }
        
        public MCPBridgeOptions endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public MCPBridgeOptions timeout(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }
        
        public MCPBridgeOptions retries(int retries) {
            this.retries = retries;
            return this;
        }
        
        public MCPBridgeOptions retryDelay(int retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        public MCPBridgeOptions debug(boolean debug) {
            this.debug = debug;
            return this;
        }
        
        public MCPBridgeOptions logger(Logger logger) {
            this.logger = logger;
            return this;
        }
    }
}