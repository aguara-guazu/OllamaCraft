package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides MCP tools for use by the Ollama AI model
 */
public class MCPToolProvider {
    
    private final MCPHttpClient httpClient;
    private final Logger logger;
    private final Gson gson;
    private final ConcurrentHashMap<String, JsonObject> toolsCache;
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 60000; // 1 minute cache
    
    /**
     * Create a new MCP tool provider
     * @param httpClient The HTTP client for MCP communication
     * @param logger Logger for debugging
     */
    public MCPToolProvider(MCPHttpClient httpClient, Logger logger) {
        this.httpClient = httpClient;
        this.logger = logger;
        this.gson = new Gson();
        this.toolsCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Get all available MCP tools for Ollama
     * @return CompletableFuture with list of tools in Ollama format
     */
    public CompletableFuture<List<JsonObject>> getToolsForOllama() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                if (isCacheValid()) {
                    debug("Returning cached tools");
                    return new ArrayList<>(toolsCache.values());
                }
                
                debug("Fetching tools from MCP server");
                
                // Request tools from MCP server
                JsonObject request = new JsonObject();
                request.addProperty("jsonrpc", "2.0");
                request.addProperty("id", "tools-list-" + System.currentTimeMillis());
                request.addProperty("method", "tools/list");
                request.add("params", new JsonObject());
                
                JsonObject response = httpClient.sendMessage(request).get(30, TimeUnit.SECONDS);
                
                if (response == null || !response.has("result")) {
                    debug("No tools found in MCP response");
                    return new ArrayList<>();
                }
                
                JsonObject result = response.getAsJsonObject("result");
                if (!result.has("tools")) {
                    debug("No tools array in MCP result");
                    return new ArrayList<>();
                }
                
                JsonArray mcpTools = result.getAsJsonArray("tools");
                List<JsonObject> ollamaTools = new ArrayList<>();
                toolsCache.clear();
                
                // Convert MCP tools to Ollama format
                for (JsonElement toolElement : mcpTools) {
                    JsonObject mcpTool = toolElement.getAsJsonObject();
                    JsonObject ollamaTool = convertMCPToolToOllama(mcpTool);
                    
                    if (ollamaTool != null) {
                        ollamaTools.add(ollamaTool);
                        String toolName = mcpTool.get("name").getAsString();
                        toolsCache.put(toolName, mcpTool);
                    }
                }
                
                lastCacheUpdate = System.currentTimeMillis();
                debug("Converted " + ollamaTools.size() + " MCP tools to Ollama format");
                
                return ollamaTools;
                
            } catch (Exception e) {
                if (logger != null) {
                    logger.log(Level.WARNING, "[MCPToolProvider] Error fetching tools from MCP server", e);
                }
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Get the original MCP tool definition by name
     * @param toolName Name of the tool
     * @return MCP tool definition or null if not found
     */
    public JsonObject getMCPTool(String toolName) {
        return toolsCache.get(toolName);
    }
    
    /**
     * Convert MCP tool definition to Ollama tool format
     * @param mcpTool MCP tool definition
     * @return Ollama tool definition
     */
    private JsonObject convertMCPToolToOllama(JsonObject mcpTool) {
        try {
            JsonObject ollamaTool = new JsonObject();
            
            // Extract basic info
            String name = mcpTool.get("name").getAsString();
            String description = mcpTool.has("description") ? 
                mcpTool.get("description").getAsString() : 
                "MCP tool: " + name;
            
            ollamaTool.addProperty("type", "function");
            
            JsonObject function = new JsonObject();
            function.addProperty("name", name);
            function.addProperty("description", description);
            
            // Convert input schema to Ollama parameters format
            if (mcpTool.has("inputSchema")) {
                JsonObject inputSchema = mcpTool.getAsJsonObject("inputSchema");
                function.add("parameters", inputSchema);
            } else {
                // Default empty parameters
                JsonObject parameters = new JsonObject();
                parameters.addProperty("type", "object");
                parameters.add("properties", new JsonObject());
                function.add("parameters", parameters);
            }
            
            ollamaTool.add("function", function);
            
            debug("Converted MCP tool: " + name);
            return ollamaTool;
            
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.WARNING, "[MCPToolProvider] Error converting MCP tool to Ollama format", e);
            }
            return null;
        }
    }
    
    /**
     * Check if the cache is still valid
     * @return True if cache is valid
     */
    private boolean isCacheValid() {
        return !toolsCache.isEmpty() && 
               (System.currentTimeMillis() - lastCacheUpdate) < CACHE_TTL_MS;
    }
    
    /**
     * Force refresh the tools cache
     */
    public CompletableFuture<Void> refreshCache() {
        return CompletableFuture.runAsync(() -> {
            lastCacheUpdate = 0; // Invalidate cache
            getToolsForOllama().join(); // Fetch new tools
        });
    }
    
    /**
     * Get the number of cached tools
     * @return Number of tools in cache
     */
    public int getCachedToolCount() {
        return toolsCache.size();
    }
    
    /**
     * Clear the tools cache
     */
    public void clearCache() {
        toolsCache.clear();
        lastCacheUpdate = 0;
    }
    
    /**
     * Log debug message if logger is available
     */
    private void debug(String message) {
        if (logger != null) {
            logger.info("[MCPToolProvider] " + message);
        }
    }
}