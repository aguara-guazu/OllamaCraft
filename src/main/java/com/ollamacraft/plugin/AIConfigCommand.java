package com.ollamacraft.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Command handler for configuring AI settings
 */
public class AIConfigCommand implements CommandExecutor, TabCompleter {

    private final OllamaCraft plugin;
    private final Map<String, List<String>> settingOptions;
    
    /**
     * Constructor for AIConfigCommand
     * @param plugin The OllamaCraft plugin instance
     */
    public AIConfigCommand(OllamaCraft plugin) {
        this.plugin = plugin;
        
        // Initialize setting options
        this.settingOptions = new HashMap<>();
        settingOptions.put("model", Arrays.asList("llama3", "mistral", "mixtral", "phi3"));
        settingOptions.put("temperature", Arrays.asList("0.0", "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0"));
        settingOptions.put("monitor-all-chat", Arrays.asList("true", "false"));
        settingOptions.put("max-context-length", Arrays.asList("10", "20", "50", "100"));
        settingOptions.put("mcp-enabled", Arrays.asList("true", "false"));
        settingOptions.put("mcp-start", new ArrayList<>());
        settingOptions.put("mcp-stop", new ArrayList<>());
        settingOptions.put("mcp-restart", new ArrayList<>());
        settingOptions.put("mcp-status", new ArrayList<>());
        settingOptions.put("reload", new ArrayList<>());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ollamacraft.config")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            showCurrentSettings(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        if (subCommand.equals("reload")) {
            handleReload(sender);
            return true;
        }
        
        // Handle MCP server management commands
        if (subCommand.equals("mcp-start")) {
            handleMcpStart(sender);
            return true;
        }
        
        if (subCommand.equals("mcp-stop")) {
            handleMcpStop(sender);
            return true;
        }
        
        if (subCommand.equals("mcp-restart")) {
            handleMcpRestart(sender);
            return true;
        }
        
        if (subCommand.equals("mcp-status")) {
            handleMcpStatus(sender);
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /aiconfig <setting> <value>").color(NamedTextColor.RED));
            return true;
        }
        
        String setting = args[0].toLowerCase();
        String value = args[1];
        
        switch (setting) {
            case "model":
                handleModelChange(sender, value);
                break;
            case "temperature":
                handleTemperatureChange(sender, value);
                break;
            case "max-context-length":
                handleMaxContextLengthChange(sender, value);
                break;
            case "monitor-all-chat":
                handleMonitorAllChatChange(sender, value);
                break;
            case "mcp-enabled":
                handleMcpEnabledChange(sender, value);
                break;
            default:
                sender.sendMessage(Component.text("Unknown setting: " + setting)
                        .color(NamedTextColor.RED));
                showAvailableSettings(sender);
                break;
        }
        
        return true;
    }
    
    /**
     * Show current AI settings to sender
     * @param sender The command sender
     */
    private void showCurrentSettings(CommandSender sender) {
        AIService aiService = plugin.getAIService();
        
        sender.sendMessage(Component.text("=== OllamaCraft Settings ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Model: ").color(NamedTextColor.YELLOW)
                .append(Component.text(aiService.getModel()).color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Temperature: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(aiService.getTemperature())).color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Max Context Length: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(plugin.getConfig().getInt("ollama.max-context-length", 50)))
                        .color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Monitor All Chat: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(plugin.getConfig().getBoolean("chat.monitor-all-chat", false)))
                        .color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("MCP Enabled: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(aiService.getMcpService().isEnabled()))
                        .color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("MCP Server Running: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(aiService.getMcpService().isRunning()))
                        .color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("MCP Server URL: ").color(NamedTextColor.YELLOW)
                .append(Component.text(aiService.getMcpService().getServerUrl())
                        .color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("MCP API Key: ").color(NamedTextColor.YELLOW)
                .append(Component.text(aiService.getMcpService().getMaskedApiKey())
                        .color(NamedTextColor.WHITE)));
    }
    
    /**
     * Show available settings to sender
     * @param sender The command sender
     */
    private void showAvailableSettings(CommandSender sender) {
        sender.sendMessage(Component.text("Available settings:").color(NamedTextColor.YELLOW));
        settingOptions.keySet().forEach(setting -> 
            sender.sendMessage(Component.text("- " + setting).color(NamedTextColor.WHITE))
        );
    }
    
    /**
     * Handle model change
     * @param sender The command sender
     * @param model The new model value
     */
    private void handleModelChange(CommandSender sender, String model) {
        plugin.getAIService().setModel(model);
        plugin.getConfig().set("ollama.model", model);
        plugin.saveConfig();
        sender.sendMessage(Component.text("AI model changed to: " + model)
                .color(NamedTextColor.GREEN));
    }
    
    /**
     * Handle temperature change
     * @param sender The command sender
     * @param temperatureStr The new temperature value as string
     */
    private void handleTemperatureChange(CommandSender sender, String temperatureStr) {
        try {
            double temperature = Double.parseDouble(temperatureStr);
            
            // Clamp value between 0.0 and 1.0
            temperature = Math.max(0.0, Math.min(1.0, temperature));
            
            plugin.getAIService().setTemperature(temperature);
            plugin.getConfig().set("ollama.temperature", temperature);
            plugin.saveConfig();
            
            sender.sendMessage(Component.text("AI temperature changed to: " + temperature)
                    .color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid temperature value. Must be a number between 0.0 and 1.0.")
                    .color(NamedTextColor.RED));
        }
    }
    
    /**
     * Handle max context length change
     * @param sender The command sender
     * @param lengthStr The new max context length as string
     */
    private void handleMaxContextLengthChange(CommandSender sender, String lengthStr) {
        try {
            int length = Integer.parseInt(lengthStr);
            
            // Ensure positive value
            if (length <= 0) {
                sender.sendMessage(Component.text("Context length must be positive.")
                        .color(NamedTextColor.RED));
                return;
            }
            
            plugin.getConfig().set("ollama.max-context-length", length);
            plugin.saveConfig();
            
            // Reload the config to apply changes
            plugin.getAIService().loadConfig();
            
            sender.sendMessage(Component.text("Max context length changed to: " + length)
                    .color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid context length. Must be a positive integer.")
                    .color(NamedTextColor.RED));
        }
    }
    
    /**
     * Handle monitor all chat change
     * @param sender The command sender
     * @param enabledStr The new monitor all chat setting as string
     */
    private void handleMonitorAllChatChange(CommandSender sender, String enabledStr) {
        boolean enabled = Boolean.parseBoolean(enabledStr);
        
        plugin.getConfig().set("chat.monitor-all-chat", enabled);
        plugin.saveConfig();
        
        sender.sendMessage(Component.text("Monitor all chat changed to: " + enabled)
                .color(NamedTextColor.GREEN));
    }
    
    /**
     * Handle MCP enabled change
     * @param sender The command sender
     * @param enabledStr The new MCP enabled setting as string
     */
    private void handleMcpEnabledChange(CommandSender sender, String enabledStr) {
        boolean enabled = Boolean.parseBoolean(enabledStr);
        
        plugin.getConfig().set("mcp.enabled", enabled);
        plugin.saveConfig();
        
        // Update MCP service
        plugin.getAIService().getMcpService().setEnabled(enabled);
        
        sender.sendMessage(Component.text("MCP integration changed to: " + enabled)
                .color(NamedTextColor.GREEN));
    }
    
    /**
     * Handle config reload
     * @param sender The command sender
     */
    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getAIService().loadConfig();
        
        sender.sendMessage(Component.text("Configuration reloaded successfully.")
                .color(NamedTextColor.GREEN));
    }
    
    /**
     * Handle MCP server start
     * @param sender The command sender
     */
    private void handleMcpStart(CommandSender sender) {
        MCPService mcpService = plugin.getAIService().getMcpService();
        
        if (!mcpService.isEnabled()) {
            sender.sendMessage(Component.text("MCP integration is disabled. Enable it first with /aiconfig mcp-enabled true")
                    .color(NamedTextColor.RED));
            return;
        }
        
        if (mcpService.isRunning()) {
            sender.sendMessage(Component.text("MCP server is already running.")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        
        sender.sendMessage(Component.text("Starting MCP bridge...")
                .color(NamedTextColor.YELLOW));
        
        mcpService.startMCPBridge().thenAccept(success -> {
            if (success) {
                sender.sendMessage(Component.text("MCP bridge started successfully.")
                        .color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to start MCP bridge. Check logs for details.")
                        .color(NamedTextColor.RED));
            }
        });
    }
    
    /**
     * Handle MCP server stop
     * @param sender The command sender
     */
    private void handleMcpStop(CommandSender sender) {
        MCPService mcpService = plugin.getAIService().getMcpService();
        
        if (!mcpService.isRunning()) {
            sender.sendMessage(Component.text("MCP server is not running.")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        
        sender.sendMessage(Component.text("Stopping MCP bridge...")
                .color(NamedTextColor.YELLOW));
        
        mcpService.stopMCPBridge().thenRun(() -> {
            sender.sendMessage(Component.text("MCP bridge stopped successfully.")
                    .color(NamedTextColor.GREEN));
        });
    }
    
    /**
     * Handle MCP server restart
     * @param sender The command sender
     */
    private void handleMcpRestart(CommandSender sender) {
        MCPService mcpService = plugin.getAIService().getMcpService();
        
        if (!mcpService.isEnabled()) {
            sender.sendMessage(Component.text("MCP integration is disabled. Enable it first with /aiconfig mcp-enabled true")
                    .color(NamedTextColor.RED));
            return;
        }
        
        sender.sendMessage(Component.text("Restarting MCP bridge...")
                .color(NamedTextColor.YELLOW));
        
        mcpService.restartMCPBridge().thenAccept(success -> {
            if (success) {
                sender.sendMessage(Component.text("MCP bridge restarted successfully.")
                        .color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to restart MCP bridge. Check logs for details.")
                        .color(NamedTextColor.RED));
            }
        });
    }
    
    /**
     * Handle MCP server status
     * @param sender The command sender
     */
    private void handleMcpStatus(CommandSender sender) {
        MCPService mcpService = plugin.getAIService().getMcpService();
        
        sender.sendMessage(Component.text("=== MCP Bridge Status ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Enabled: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(mcpService.isEnabled()))
                        .color(mcpService.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("Running: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(mcpService.isRunning()))
                        .color(mcpService.isRunning() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("Implementation: ").color(NamedTextColor.YELLOW)
                .append(Component.text("Native Java Bridge").color(NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("Server URL: ").color(NamedTextColor.YELLOW)
                .append(Component.text(mcpService.getServerUrl()).color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("API Key: ").color(NamedTextColor.YELLOW)
                .append(Component.text(mcpService.getMaskedApiKey()).color(NamedTextColor.WHITE)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return settingOptions.keySet().stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String setting = args[0].toLowerCase();
            List<String> options = settingOptions.getOrDefault(setting, new ArrayList<>());
            
            return options.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}