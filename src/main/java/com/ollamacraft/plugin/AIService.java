package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ollamacraft.plugin.model.ChatMessage;
import com.ollamacraft.plugin.model.MessageHistory;
import okhttp3.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Service to interact with Ollama AI API
 */
public class AIService {
    
    private final OllamaCraft plugin;
    private final OkHttpClient client;
    private final Gson gson;
    private final MessageHistory messageHistory;
    private final MCPService mcpService;
    
    private String apiUrl;
    private String model;
    private double temperature;
    private String systemPrompt;
    private int maxContextLength;
    
    /**
     * Constructor for AIService
     * @param plugin The OllamaCraft plugin instance
     */
    public AIService(OllamaCraft plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.messageHistory = new MessageHistory();
        this.mcpService = new MCPService(plugin);
        
        // Initialize HTTP client
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        
        // Load configuration
        loadConfig();
    }
    
    /**
     * Load configuration from config.yml
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        this.apiUrl = config.getString("ollama.api-url", "http://localhost:11434/api");
        this.model = config.getString("ollama.model", "llama3");
        this.temperature = config.getDouble("ollama.temperature", 0.7);
        this.systemPrompt = config.getString("ollama.system-prompt", 
                "You are Steve, a helpful assistant in a Minecraft world.");
        this.maxContextLength = config.getInt("ollama.max-context-length", 50);
        
        // Update message history max length
        messageHistory.setMaxHistoryLength(maxContextLength);
        
        // Initialize MCP service
        if (config.getBoolean("mcp.enabled", true)) {
            mcpService.initialize();
        }
    }
    
    /**
     * Send a chat message to the AI and get a response
     * @param player The player sending the message
     * @param content The message content
     * @return The AI's response
     */
    public String sendChatMessage(Player player, String content) {
        try {
            // Add user message to history
            messageHistory.addMessage(new ChatMessage("user", player.getName() + ": " + content));
            
            // Create request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("system", systemPrompt);
            
            // Add messages history
            JsonArray messages = new JsonArray();
            
            // Add system message if configured
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", "system");
                systemMessage.addProperty("content", systemPrompt);
                messages.add(systemMessage);
            }
            
            // Add chat history
            for (ChatMessage message : messageHistory.getMessages()) {
                JsonObject chatMessage = new JsonObject();
                chatMessage.addProperty("role", message.getRole());
                chatMessage.addProperty("content", message.getContent());
                messages.add(chatMessage);
            }
            
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("stream", false);
            
            // Build HTTP request
            Request request = new Request.Builder()
                .url(apiUrl + "/chat")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();
            
            // Execute request
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }
            
            // Parse response
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            // Get message content
            String aiResponse = "";
            if (jsonResponse.has("message") && jsonResponse.getAsJsonObject("message").has("content")) {
                aiResponse = jsonResponse.getAsJsonObject("message").get("content").getAsString();
            }
            
            // Add assistant response to history
            messageHistory.addMessage(new ChatMessage("assistant", aiResponse));
            
            // Check for MCP commands in the response
            if (mcpService.isEnabled()) {
                mcpService.processAIResponse(aiResponse, player);
            }
            
            return aiResponse;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error sending chat message to AI", e);
            return "Sorry, I encountered an error processing your request.";
        }
    }
    
    /**
     * Clear the message history for all users
     */
    public void clearHistory() {
        messageHistory.clearHistory();
    }
    
    /**
     * Clean up resources when the service is shutting down
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
    
    /**
     * Get the current AI model
     * @return The model name
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Set the AI model
     * @param model The model name
     */
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * Get the current temperature setting
     * @return The temperature value
     */
    public double getTemperature() {
        return temperature;
    }
    
    /**
     * Set the temperature value
     * @param temperature The temperature value
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    /**
     * Get the current system prompt
     * @return The system prompt
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    /**
     * Set the system prompt
     * @param systemPrompt The system prompt
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    /**
     * Get the MCP service
     * @return The MCP service instance
     */
    public MCPService getMcpService() {
        return mcpService;
    }
}