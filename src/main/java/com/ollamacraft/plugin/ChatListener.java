package com.ollamacraft.plugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Listener for chat events to enable AI interactions
 */
public class ChatListener implements Listener {
    
    private final OllamaCraft plugin;
    private boolean monitorAllChat;
    private String triggerPrefix;
    private String responseFormat;
    
    /**
     * Constructor for ChatListener
     * @param plugin The OllamaCraft plugin instance
     */
    public ChatListener(OllamaCraft plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * Load configuration values
     */
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.monitorAllChat = config.getBoolean("chat.monitor-all-chat", false);
        this.triggerPrefix = config.getString("chat.trigger-prefix", "Steve, ");
        this.responseFormat = config.getString("chat.response-format", "[Steve] &a%message%");
    }
    
    /**
     * Handle async chat events to process AI interactions
     * @param event The AsyncChatEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        
        // Extract message content
        if (!(event.message() instanceof TextComponent textComponent)) {
            return;
        }
        
        String message = textComponent.content();
        
        // Check if the message should trigger an AI response
        if (shouldProcessMessage(message)) {
            // Remove prefix if present
            String query = message;
            if (message.startsWith(triggerPrefix)) {
                query = message.substring(triggerPrefix.length()).trim();
            }
            
            // Don't cancel the event, let the chat message go through
            
            // Process AI query asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    // Get response from AI
                    String aiResponse = plugin.getAIService().sendChatMessage(player, query);
                    
                    // Format the response
                    String formattedResponse = formatResponse(aiResponse);
                    
                    // Send response on the main thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Broadcast to all players
                        plugin.getServer().broadcast(Component.text(formattedResponse)
                                .color(NamedTextColor.GREEN));
                    });
                } catch (Exception e) {
                    plugin.getLogger().severe("Error processing AI response: " + e.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("An error occurred while processing your request.")
                                .color(NamedTextColor.RED));
                    });
                }
            });
        }
    }
    
    /**
     * Check if a message should trigger an AI response
     * @param message The chat message
     * @return True if the message should be processed
     */
    private boolean shouldProcessMessage(String message) {
        if (monitorAllChat) {
            return true;
        }
        
        return message.startsWith(triggerPrefix);
    }
    
    /**
     * Format the AI response according to configuration
     * @param response The raw AI response
     * @return The formatted response
     */
    private String formatResponse(String response) {
        if (responseFormat == null || responseFormat.isEmpty()) {
            return response;
        }
        
        return responseFormat.replace("%message%", response)
                .replace("&a", "") // Simple color code handling
                .replace("&b", "")
                .replace("&c", "")
                .replace("&e", "")
                .replace("&f", "")
                .replace("&r", "");
    }
}