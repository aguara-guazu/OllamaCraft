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
 * Now includes intelligent response detection
 */
public class ChatListener implements Listener {
    
    private final OllamaCraft plugin;
    private boolean monitorAllChat;
    private String triggerPrefix;
    private String responseFormat;
    
    // Intelligent detection service
    private ResponseDetectionService detectionService;
    
    /**
     * Constructor for ChatListener
     * @param plugin The OllamaCraft plugin instance
     */
    public ChatListener(OllamaCraft plugin) {
        this.plugin = plugin;
        loadConfig();
        initializeDetectionService();
    }
    
    /**
     * Initialize the intelligent detection service
     */
    private void initializeDetectionService() {
        try {
            // Note: This would be initialized in the actual plugin startup
            // For now, we'll handle the case where it might be null
            // detectionService = plugin.getDetectionService();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize detection service: " + e.getMessage());
        }
    }
    
    /**
     * Set the detection service (called from plugin initialization)
     */
    public void setDetectionService(ResponseDetectionService detectionService) {
        this.detectionService = detectionService;
        plugin.getLogger().info("Intelligent detection service enabled in chat listener");
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
        
        // Check if the message should trigger an AI response using intelligent detection
        if (detectionService != null) {
            // Use intelligent detection service
            detectionService.analyzeMessage(player, message)
                .thenAccept(detectionResult -> {
                    plugin.getLogger().fine("Detection result: " + detectionResult.shouldRespond() + 
                                          " (confidence: " + detectionResult.getConfidence() + 
                                          ", method: " + detectionResult.getMethod() + 
                                          ", reason: " + detectionResult.getReason() + ")");
                    
                    if (detectionResult.shouldRespond() && 
                        detectionResult.getConfidence() >= plugin.getAIConfiguration().getConfidenceThreshold()) {
                        processAIResponse(player, message, detectionResult);
                    } else if (detectionResult.shouldRespond()) {
                        plugin.getLogger().fine("Detection result below confidence threshold: " + 
                                              detectionResult.getConfidence() + " < " + 
                                              plugin.getAIConfiguration().getConfidenceThreshold());
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Detection analysis failed: " + throwable.getMessage());
                    
                    // Fallback to traditional method if enabled
                    if (shouldProcessMessageFallback(message)) {
                        processAIResponse(player, message, null);
                    }
                    return null;
                });
        } else {
            // Fallback to traditional detection
            if (shouldProcessMessageFallback(message)) {
                processAIResponse(player, message, null);
            }
        }
    }
    
    /**
     * Process AI response based on detection result
     * @param player The player who sent the message
     * @param message The original message
     * @param detectionResult Detection result (may be null for fallback mode)
     */
    private void processAIResponse(Player player, String message, ResponseDetectionService.DetectionResult detectionResult) {
        // Don't cancel the original chat event, let the message go through
        
        // Determine query to process
        final String queryToProcess = message.startsWith(triggerPrefix) 
            ? message.substring(triggerPrefix.length()).trim() 
            : message;
            
        CompletableFuture.runAsync(() -> {
            try {
                // Add to context for future detection
                if (detectionService != null) {
                    detectionService.addToContext(player, message, false);
                }
                
                // Get response from AI
                String aiResponse = plugin.getAIService().sendChatMessage(player, queryToProcess);
                
                // Add AI response to context
                if (detectionService != null) {
                    detectionService.addToContext(player, aiResponse, true);
                }
                
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
    
    /**
     * Fallback method to check if a message should trigger an AI response
     * @param message The chat message
     * @return True if the message should be processed
     */
    private boolean shouldProcessMessageFallback(String message) {
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