package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Interface for converting tools between different AI provider formats
 * Handles conversion from MCP format to provider-specific tool definitions
 */
public interface ToolConverter {
    
    /**
     * Convert a single MCP tool to provider-specific format
     * @param mcpTool Tool in MCP format
     * @return Tool in provider format, or null if conversion fails
     */
    JsonObject convertTool(JsonObject mcpTool);
    
    /**
     * Convert a list of MCP tools to provider-specific format
     * @param mcpTools List of tools in MCP format
     * @return List of tools in provider format
     */
    List<JsonObject> convertTools(List<JsonObject> mcpTools);
    
    /**
     * Convert a tool call result back to standard format
     * @param providerResult Tool call result from provider
     * @return Standardized tool call result
     */
    JsonObject convertToolResult(JsonObject providerResult);
    
    /**
     * Get the provider name this converter is for
     * @return Provider name
     */
    String getProviderName();
    
    /**
     * Validate that a tool is properly formatted for this provider
     * @param tool Tool to validate
     * @return true if valid, false otherwise
     */
    boolean validateTool(JsonObject tool);
    
    /**
     * Get example tool format for documentation
     * @return Example tool in provider format
     */
    JsonObject getExampleTool();
}