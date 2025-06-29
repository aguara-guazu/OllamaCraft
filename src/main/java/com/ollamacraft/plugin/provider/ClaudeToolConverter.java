package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Tool converter for Claude/Anthropic provider
 * Converts MCP tools to Claude's tool format
 */
public class ClaudeToolConverter implements ToolConverter {
    
    private final Logger logger;
    
    public ClaudeToolConverter(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public JsonObject convertTool(JsonObject mcpTool) {
        try {
            if (!mcpTool.has("function")) {
                logger.warning("MCP tool missing 'function' field for Claude conversion");
                return null;
            }
            
            JsonObject function = mcpTool.getAsJsonObject("function");
            String name = function.get("name").getAsString();
            String description = function.has("description") ? 
                function.get("description").getAsString() : "Tool: " + name;
            
            JsonObject claudeTool = new JsonObject();
            claudeTool.addProperty("name", name);
            claudeTool.addProperty("description", description);
            
            // Convert parameters to input_schema
            if (function.has("parameters")) {
                JsonObject parameters = function.getAsJsonObject("parameters");
                claudeTool.add("input_schema", parameters);
            } else {
                // Default schema
                JsonObject defaultSchema = new JsonObject();
                defaultSchema.addProperty("type", "object");
                defaultSchema.add("properties", new JsonObject());
                claudeTool.add("input_schema", defaultSchema);
            }
            
            return claudeTool;
            
        } catch (Exception e) {
            logger.warning("Failed to convert tool for Claude: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<JsonObject> convertTools(List<JsonObject> mcpTools) {
        List<JsonObject> converted = new ArrayList<>();
        
        for (JsonObject tool : mcpTools) {
            JsonObject convertedTool = convertTool(tool);
            if (convertedTool != null) {
                converted.add(convertedTool);
            }
        }
        
        logger.info("Converted " + converted.size() + "/" + mcpTools.size() + " tools for Claude");
        return converted;
    }
    
    @Override
    public JsonObject convertToolResult(JsonObject providerResult) {
        // Claude returns tool results in specific format, convert to standard
        try {
            JsonObject standardResult = new JsonObject();
            
            if (providerResult.has("content")) {
                standardResult.add("content", providerResult.get("content"));
            }
            if (providerResult.has("tool_use_id")) {
                standardResult.add("tool_call_id", providerResult.get("tool_use_id"));
            }
            if (providerResult.has("is_error")) {
                standardResult.add("error", providerResult.get("is_error"));
            }
            
            return standardResult;
            
        } catch (Exception e) {
            logger.warning("Failed to convert Claude tool result: " + e.getMessage());
            return providerResult;
        }
    }
    
    @Override
    public String getProviderName() {
        return "claude";
    }
    
    @Override
    public boolean validateTool(JsonObject tool) {
        return tool.has("name") && 
               tool.has("description") && 
               tool.has("input_schema");
    }
    
    @Override
    public JsonObject getExampleTool() {
        JsonObject example = new JsonObject();
        example.addProperty("name", "example_tool");
        example.addProperty("description", "An example tool for Claude");
        
        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        JsonObject paramExample = new JsonObject();
        paramExample.addProperty("type", "string");
        paramExample.addProperty("description", "Example parameter");
        properties.add("example_param", paramExample);
        
        inputSchema.add("properties", properties);
        
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("example_param");
        inputSchema.add("required", required);
        
        example.add("input_schema", inputSchema);
        
        return example;
    }
}