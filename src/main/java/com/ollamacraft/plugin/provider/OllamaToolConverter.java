package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Tool converter for Ollama provider
 * Ollama uses the standard OpenAI tool format
 */
public class OllamaToolConverter implements ToolConverter {
    
    private final Logger logger;
    
    public OllamaToolConverter(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public JsonObject convertTool(JsonObject mcpTool) {
        try {
            if (!mcpTool.has("function")) {
                logger.warning("MCP tool missing 'function' field for Ollama conversion");
                return null;
            }
            
            // Ollama uses the same format as OpenAI - return as-is
            return mcpTool.deepCopy();
            
        } catch (Exception e) {
            logger.warning("Failed to convert tool for Ollama: " + e.getMessage());
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
        
        logger.info("Converted " + converted.size() + "/" + mcpTools.size() + " tools for Ollama");
        return converted;
    }
    
    @Override
    public JsonObject convertToolResult(JsonObject providerResult) {
        // Ollama returns results in standard format
        return providerResult.deepCopy();
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    @Override
    public boolean validateTool(JsonObject tool) {
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
        function.addProperty("description", "An example tool for demonstration");
        
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        JsonObject paramExample = new JsonObject();
        paramExample.addProperty("type", "string");
        paramExample.addProperty("description", "Example parameter");
        properties.add("example_param", paramExample);
        
        parameters.add("properties", properties);
        
        JsonObject required = new JsonObject();
        required.add("required", new com.google.gson.JsonArray());
        
        function.add("parameters", parameters);
        example.add("function", function);
        
        return example;
    }
}