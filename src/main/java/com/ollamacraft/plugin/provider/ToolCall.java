package com.ollamacraft.plugin.provider;

import com.google.gson.JsonObject;

/**
 * Unified representation of a tool call from any AI provider
 * Standardizes tool calls from different AI services
 */
public class ToolCall {
    
    private final String id;
    private final String name;
    private final JsonObject arguments;
    private final String reasoning;
    
    public ToolCall(String id, String name, JsonObject arguments, String reasoning) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
        this.reasoning = reasoning;
    }
    
    public ToolCall(String id, String name, JsonObject arguments) {
        this(id, name, arguments, null);
    }
    
    public ToolCall(String name, JsonObject arguments) {
        this(generateId(), name, arguments, null);
    }
    
    public ToolCall(String name, JsonObject arguments, String reasoning) {
        this(generateId(), name, arguments, reasoning);
    }
    
    /**
     * Get the tool call ID (for tracking responses)
     * @return Tool call ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the name of the tool to call
     * @return Tool name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the arguments for the tool call
     * @return Tool arguments as JsonObject
     */
    public JsonObject getArguments() {
        return arguments;
    }
    
    /**
     * Get the reasoning behind this tool call (if provided)
     * @return Reasoning text or null
     */
    public String getReasoning() {
        return reasoning;
    }
    
    /**
     * Check if this tool call has reasoning
     * @return true if reasoning is provided
     */
    public boolean hasReasoning() {
        return reasoning != null && !reasoning.trim().isEmpty();
    }
    
    /**
     * Convert to MCP format for execution
     * @return JsonObject in MCP format
     */
    public JsonObject toMCPFormat() {
        JsonObject mcpCall = new JsonObject();
        mcpCall.addProperty("name", name);
        mcpCall.add("arguments", arguments);
        return mcpCall;
    }
    
    /**
     * Convert to MCPToolExecutor expected format
     * @return JsonObject in MCPToolExecutor format
     */
    public JsonObject toMCPExecutorFormat() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.add("arguments", arguments);
        
        JsonObject mcpCall = new JsonObject();
        mcpCall.add("function", function);
        
        return mcpCall;
    }
    
    /**
     * Generate a unique ID for tool calls
     * @return Unique ID string
     */
    private static String generateId() {
        return "call_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }
    
    @Override
    public String toString() {
        return "ToolCall{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ToolCall toolCall = (ToolCall) o;
        
        if (!id.equals(toolCall.id)) return false;
        if (!name.equals(toolCall.name)) return false;
        return arguments.equals(toolCall.arguments);
    }
    
    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + arguments.hashCode();
        return result;
    }
}