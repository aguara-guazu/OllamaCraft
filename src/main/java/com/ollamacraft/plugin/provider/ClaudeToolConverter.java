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
            // Handle both original MCP format and Ollama format for backward compatibility
            JsonObject toolToConvert = mcpTool;
            
            // If it's already in Ollama format, extract the function
            if (mcpTool.has("function")) {
                toolToConvert = mcpTool.getAsJsonObject("function");
            }
            
            // Now toolToConvert should have name, description, and parameters (or inputSchema)
            if (!toolToConvert.has("name")) {
                logger.warning("MCP tool missing 'name' field for Claude conversion");
                return null;
            }
            
            String name = toolToConvert.get("name").getAsString();
            String description = toolToConvert.has("description") ? 
                toolToConvert.get("description").getAsString() : "Tool: " + name;
            
            JsonObject claudeTool = new JsonObject();
            claudeTool.addProperty("name", name);
            claudeTool.addProperty("description", description);
            
            // Convert parameters/inputSchema to input_schema for Claude
            JsonObject inputSchema = null;
            if (toolToConvert.has("inputSchema")) {
                // Original MCP format
                inputSchema = toolToConvert.getAsJsonObject("inputSchema");
            } else if (toolToConvert.has("parameters")) {
                // Ollama format or OpenAI format
                inputSchema = toolToConvert.getAsJsonObject("parameters");
            }
            
            if (inputSchema != null) {
                claudeTool.add("input_schema", inputSchema);
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