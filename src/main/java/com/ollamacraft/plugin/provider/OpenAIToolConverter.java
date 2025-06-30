package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Tool converter for OpenAI provider
 * Converts MCP tools to OpenAI's function calling format
 */
public class OpenAIToolConverter implements ToolConverter {
    
    private final Logger logger;
    
    public OpenAIToolConverter(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public JsonObject convertTool(JsonObject mcpTool) {
        try {
            JsonObject function = new JsonObject();
            
            // Handle both original MCP format and Ollama format for backward compatibility
            if (mcpTool.has("function")) {
                // Already in Ollama format - extract the function
                function = mcpTool.getAsJsonObject("function");
            } else {
                // Original MCP format - convert to OpenAI function format
                if (!mcpTool.has("name")) {
                    logger.warning("MCP tool missing 'name' field for OpenAI conversion");
                    return null;
                }
                
                String name = mcpTool.get("name").getAsString();
                String description = mcpTool.has("description") ? 
                    mcpTool.get("description").getAsString() : "Tool: " + name;
                
                function.addProperty("name", name);
                function.addProperty("description", description);
                
                // Convert inputSchema to parameters for OpenAI
                if (mcpTool.has("inputSchema")) {
                    JsonObject inputSchema = mcpTool.getAsJsonObject("inputSchema");
                    function.add("parameters", inputSchema);
                } else {
                    // Default parameters
                    JsonObject parameters = new JsonObject();
                    parameters.addProperty("type", "object");
                    parameters.add("properties", new JsonObject());
                    function.add("parameters", parameters);
                }
            }
            
            JsonObject openAITool = new JsonObject();
            openAITool.addProperty("type", "function");
            openAITool.add("function", function);
            
            return openAITool;
            
        } catch (Exception e) {
            logger.warning("Failed to convert tool for OpenAI: " + e.getMessage());
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
        
        logger.info("Converted " + converted.size() + "/" + mcpTools.size() + " tools for OpenAI");
        return converted;
    }
    
    @Override
    public JsonObject convertToolResult(JsonObject providerResult) {
        // OpenAI returns results in standard format
        return providerResult.deepCopy();
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public boolean validateTool(JsonObject tool) {
        if (!tool.has("type") || !"function".equals(tool.get("type").getAsString())) {
            return false;
        }
        
        if (!tool.has("function")) {
            return false;
        }
        
        JsonObject function = tool.getAsJsonObject("function");
        return function.has("name") && function.has("description");
    }
    
    @Override
    public JsonObject getExampleTool() {
        JsonObject example = new JsonObject();
        example.addProperty("type", "function");
        
        JsonObject function = new JsonObject();
        function.addProperty("name", "example_tool");
        function.addProperty("description", "An example tool for OpenAI");
        
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        JsonObject paramExample = new JsonObject();
        paramExample.addProperty("type", "string");
        paramExample.addProperty("description", "Example parameter");
        properties.add("example_param", paramExample);
        
        parameters.add("properties", properties);
        
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("example_param");
        parameters.add("required", required);
        
        function.add("parameters", parameters);
        example.add("function", function);
        
        return example;
    }
}