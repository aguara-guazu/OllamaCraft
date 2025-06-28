package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to interact with MinecraftMCP API for executing commands
 */
public class MCPService {
    
    private final OllamaCraft plugin;
    private final OkHttpClient client;
    private final Gson gson;
    
    private boolean enabled;
    private String apiUrl;
    private String apiKey;
    private List<String> allowedCommands;
    
    // Pattern to find commands in AI responses like /give, /teleport, etc.
    private static final Pattern COMMAND_PATTERN = Pattern.compile("/([a-zA-Z0-9_]+)\\s+(.*?)(?:\\s|$)");
    
    // Pattern to detect command intentions in natural language
    private static final Pattern INTENT_PATTERN = Pattern.compile("(?i)(give|teleport|spawn|summon|time|weather)\\s+(to|for|the|a|an)?\\s*([a-zA-Z0-9_\\s]+)");
    
    /**
     * Constructor for MCPService
     * @param plugin The OllamaCraft plugin instance
     */
    public MCPService(OllamaCraft plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        
        // Initialize HTTP client
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        
        this.enabled = false;
    }
    
    /**
     * Initialize the MCP service from configuration
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        
        this.enabled = config.getBoolean("mcp.enabled", true);
        this.apiUrl = config.getString("mcp.api-url", "http://localhost:25585/mcp");
        this.apiKey = config.getString("mcp.api-key", "");
        this.allowedCommands = config.getStringList("mcp.allowed-commands");
        
        if (enabled && (apiKey == null || apiKey.isEmpty())) {
            plugin.getLogger().warning("MCP integration is enabled but API key is not set. Commands will not be executed.");
            enabled = false;
        }
    }
    
    /**
     * Process an AI response to extract and execute commands
     * @param response The AI response to process
     * @param player The player who the AI is responding to
     */
    public void processAIResponse(String response, Player player) {
        if (!enabled) {
            return;
        }
        
        // Extract explicit commands (format: "/command args")
        Matcher commandMatcher = COMMAND_PATTERN.matcher(response);
        while (commandMatcher.find()) {
            String commandName = commandMatcher.group(1);
            String commandArgs = commandMatcher.group(2);
            
            if (isCommandAllowed(commandName)) {
                executeCommand(commandName, commandArgs, player);
            }
        }
        
        // Extract command intents from natural language
        Matcher intentMatcher = INTENT_PATTERN.matcher(response);
        while (intentMatcher.find()) {
            String intent = intentMatcher.group(1).toLowerCase();
            String target = intentMatcher.group(3).trim();
            
            // Map intent to command
            switch (intent) {
                case "give":
                    if (isCommandAllowed("give") && target.contains(" ")) {
                        String[] parts = target.split(" ", 2);
                        String item = parts[0];
                        String playerName = parts.length > 1 ? parts[1] : player.getName();
                        executeCommand("give", playerName + " " + item + " 1", player);
                    }
                    break;
                case "teleport":
                    if (isCommandAllowed("teleport") || isCommandAllowed("tp")) {
                        executeCommand("tp", player.getName() + " " + target, player);
                    }
                    break;
                case "summon":
                case "spawn":
                    if (isCommandAllowed("summon")) {
                        executeCommand("summon", target + " ~ ~ ~", player);
                    }
                    break;
                case "time":
                    if (isCommandAllowed("time")) {
                        executeCommand("time", "set " + target, player);
                    }
                    break;
                case "weather":
                    if (isCommandAllowed("weather")) {
                        executeCommand("weather", target, player);
                    }
                    break;
            }
        }
    }
    
    /**
     * Check if a command is allowed to be executed
     * @param commandName The command name to check
     * @return True if the command is allowed
     */
    private boolean isCommandAllowed(String commandName) {
        // If no allowed commands are specified, all commands are allowed
        if (allowedCommands == null || allowedCommands.isEmpty()) {
            return true;
        }
        
        return allowedCommands.contains(commandName);
    }
    
    /**
     * Execute a Minecraft command via MCP API
     * @param commandName The command name
     * @param args The command arguments
     * @param player The player who triggered the command
     */
    private void executeCommand(String commandName, String args, Player player) {
        try {
            // Build request object
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("jsonrpc", "2.0");
            requestBody.addProperty("id", 1);
            requestBody.addProperty("method", "minecraft_execute_command");
            
            // Build params object
            JsonObject params = new JsonObject();
            params.addProperty("command", commandName + " " + args);
            requestBody.add("params", params);
            
            // Build HTTP request
            Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
            
            // Execute request
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                plugin.getLogger().warning("Failed to execute command via MCP: " + response.code());
                return;
            }
            
            // Log command execution
            plugin.getLogger().info("Executed command via MCP: " + commandName + " " + args + " for player " + player.getName());
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error executing command via MCP", e);
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
    }
}