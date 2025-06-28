package com.ollamacraft.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for interacting with AI via commands
 */
public class AICommand implements CommandExecutor, TabCompleter {

    private final OllamaCraft plugin;
    private final List<String> subcommands = Arrays.asList("ask", "clear");
    
    /**
     * Constructor for AICommand
     * @param plugin The OllamaCraft plugin instance
     */
    public AICommand(OllamaCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "ask":
                handleAskCommand(player, args);
                break;
            case "clear":
                handleClearCommand(player);
                break;
            default:
                // If the first argument isn't a subcommand, treat the entire args as a query
                String query = String.join(" ", args);
                sendQueryToAI(player, query);
                break;
        }

        return true;
    }

    /**
     * Handle the "ask" subcommand
     * @param player The player who sent the command
     * @param args The command arguments
     */
    private void handleAskCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Please provide a question for the AI.")
                    .color(NamedTextColor.RED));
            return;
        }

        // Combine all arguments after "ask" into the query
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        sendQueryToAI(player, query);
    }

    /**
     * Handle the "clear" subcommand
     * @param player The player who sent the command
     */
    private void handleClearCommand(Player player) {
        plugin.getAIService().clearHistory();
        player.sendMessage(Component.text("AI conversation history has been cleared.")
                .color(NamedTextColor.GREEN));
    }

    /**
     * Send a query to the AI and handle the response
     * @param player The player who sent the query
     * @param query The query text
     */
    private void sendQueryToAI(Player player, String query) {
        player.sendMessage(Component.text("Sending your query to the AI...")
                .color(NamedTextColor.GRAY));

        // Process the query asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String response = plugin.getAIService().sendChatMessage(player, query);

                // Send response on the main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Format the response for in-game display
                    String formattedSender = "[Steve]";
                    Component responseComponent = Component.text(formattedSender)
                            .color(NamedTextColor.AQUA)
                            .append(Component.text(" " + response)
                            .color(NamedTextColor.WHITE));

                    // Broadcast the response to all players
                    plugin.getServer().broadcast(responseComponent);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing AI query: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("An error occurred while processing your query.")
                            .color(NamedTextColor.RED));
                });
            }
        });
    }

    /**
     * Send help message to player
     * @param player The player to send help to
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(Component.text("=== OllamaCraft Commands ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/ai <message>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Send a message to the AI").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/ai ask <message>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Same as above").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/ai clear").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Clear conversation history").color(NamedTextColor.WHITE)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}