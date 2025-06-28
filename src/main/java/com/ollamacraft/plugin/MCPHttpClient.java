package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for communicating with MCP servers
 */
public class MCPHttpClient {
    
    private final OkHttpClient httpClient;
    private final String serverUrl;
    private final String apiKey;
    private final String endpoint;
    private final int retries;
    private final int retryDelayMs;
    private final boolean debug;
    private final Logger logger;
    private final Gson gson;
    
    /**
     * Create a new HTTP client for MCP communication
     * @param options Configuration options
     */
    public MCPHttpClient(MCPHttpClientOptions options) {
        this.serverUrl = options.serverUrl;
        this.apiKey = options.apiKey;
        this.endpoint = options.endpoint;
        this.retries = options.retries;
        this.retryDelayMs = options.retryDelayMs;
        this.debug = options.debug;
        this.logger = options.logger;
        this.gson = new Gson();
        
        // Build HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(options.timeoutMs))
                .readTimeout(Duration.ofMillis(options.timeoutMs))
                .writeTimeout(Duration.ofMillis(options.timeoutMs))
                .build();
    }
    
    /**
     * Log debug message if debug mode is enabled
     */
    private void debug(String message) {
        if (debug && logger != null) {
            logger.info("[MCPHttpClient] " + message);
        }
    }
    
    /**
     * Log debug message with data if debug mode is enabled
     */
    private void debug(String message, Object data) {
        if (debug && logger != null) {
            logger.info("[MCPHttpClient] " + message + ": " + gson.toJson(data));
        }
    }
    
    /**
     * Send a JSON-RPC message to the MCP server
     * @param message The JSON-RPC message to send
     * @return CompletableFuture with the server's response
     */
    public CompletableFuture<JsonObject> sendMessage(JsonObject message) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = retries + 1;
            Exception lastError = null;
            
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    String url = serverUrl + endpoint;
                    debug("Sending request to " + url);
                    debug("Request payload", message);
                    
                    // Build request
                    RequestBody body = RequestBody.create(
                        gson.toJson(message),
                        MediaType.get("application/json")
                    );
                    
                    Request.Builder requestBuilder = new Request.Builder()
                            .url(url)
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("X-API-Key", apiKey);
                    
                    Request request = requestBuilder.build();
                    
                    // Execute request
                    try (Response response = httpClient.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("HTTP " + response.code() + ": " + response.message());
                        }
                        
                        ResponseBody responseBody = response.body();
                        if (responseBody == null) {
                            throw new IOException("Empty response body");
                        }
                        
                        String responseJson = responseBody.string();
                        JsonObject responseObject = gson.fromJson(responseJson, JsonObject.class);
                        
                        debug("Received response", responseObject);
                        return responseObject;
                    }
                    
                } catch (Exception error) {
                    lastError = error;
                    
                    if (debug && logger != null) {
                        logger.log(Level.WARNING, "[MCPHttpClient] Request failed (attempt " + attempt + "/" + attempts + "): " + error.getMessage());
                    }
                    
                    if (attempt < attempts) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry delay", ie);
                        }
                    }
                }
            }
            
            // Handle final error with better details
            String errorMessage;
            if (lastError instanceof IOException) {
                if (lastError.getMessage().contains("Connection refused")) {
                    errorMessage = "Connection refused at " + serverUrl + ". Is the MCP server running?";
                } else if (lastError.getMessage().contains("timeout")) {
                    errorMessage = "Connection timed out. Try increasing the timeout.";
                } else if (lastError.getMessage().contains("HTTP 401") || lastError.getMessage().contains("HTTP 403")) {
                    errorMessage = "Authentication failed. Please check your API key.";
                } else if (lastError.getMessage().contains("HTTP 404")) {
                    errorMessage = "MCP endpoint not found. Check the server URL and endpoint path.";
                } else {
                    errorMessage = lastError.getMessage();
                }
            } else {
                errorMessage = lastError.getMessage();
            }
            
            throw new RuntimeException("Failed to communicate with MCP server: " + errorMessage, lastError);
        });
    }
    
    /**
     * Test connection to the HTTP MCP server
     * @return CompletableFuture<Boolean> - True if connection successful
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                debug("Testing connection to " + serverUrl + endpoint + "...");
                
                // Simple request to test connection
                JsonObject testMessage = new JsonObject();
                testMessage.addProperty("jsonrpc", "2.0");
                testMessage.addProperty("id", "connection-test");
                testMessage.addProperty("method", "tools/list");
                testMessage.add("params", new JsonObject());
                
                JsonObject response = sendMessage(testMessage).get(30, TimeUnit.SECONDS);
                
                if (response != null && response.has("jsonrpc") && "2.0".equals(response.get("jsonrpc").getAsString())) {
                    debug("Successfully connected to MCP server");
                    
                    // Check server capabilities if available
                    if (response.has("result") && response.getAsJsonObject("result").has("tools")) {
                        JsonObject result = response.getAsJsonObject("result");
                        int toolCount = result.getAsJsonArray("tools").size();
                        debug("Server capabilities: " + toolCount + " tools available");
                    }
                    
                    return true;
                } else {
                    throw new RuntimeException("Invalid JSON-RPC response format");
                }
            } catch (Exception error) {
                if (logger != null) {
                    logger.log(Level.WARNING, "[MCPHttpClient] Connection test failed: " + error.getMessage());
                }
                throw new RuntimeException("Connection test failed", error);
            }
        });
    }
    
    /**
     * Configuration options for MCPHttpClient
     */
    public static class MCPHttpClientOptions {
        public String serverUrl;
        public String apiKey;
        public String endpoint = "/mcp";
        public int timeoutMs = 30000;
        public int retries = 3;
        public int retryDelayMs = 1000;
        public boolean debug = false;
        public Logger logger;
        
        public MCPHttpClientOptions(String serverUrl, String apiKey) {
            this.serverUrl = serverUrl;
            this.apiKey = apiKey;
        }
        
        public MCPHttpClientOptions endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public MCPHttpClientOptions timeout(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }
        
        public MCPHttpClientOptions retries(int retries) {
            this.retries = retries;
            return this;
        }
        
        public MCPHttpClientOptions retryDelay(int retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        public MCPHttpClientOptions debug(boolean debug) {
            this.debug = debug;
            return this;
        }
        
        public MCPHttpClientOptions logger(Logger logger) {
            this.logger = logger;
            return this;
        }
    }
}