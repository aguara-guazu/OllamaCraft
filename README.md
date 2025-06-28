# OllamaCraft

OllamaCraft is a PaperMC plugin that allows Minecraft players to interact with an AI assistant powered by Ollama. The AI assistant can respond to player questions in the game chat and even perform in-game actions using the MinecraftMCP protocol.

![OllamaCraft](https://github.com/aguara-guazu/OllamaCraft/raw/main/docs/images/ollamacraft_logo.png)

## Features

- Talk to an AI assistant directly in Minecraft chat
- Configure which AI model to use from Ollama
- Maintains conversation history for context
- AI can execute Minecraft commands through MCP integration
- Configurable chat triggers and response formatting
- Extensible architecture for adding more AI features

## Requirements

- PaperMC server (version 1.21.4 or newer)
- Java 17 or newer
- Ollama server running locally or on an accessible server
- MinecraftMCP plugin (optional, for command execution)

## Installation

1. Download the latest release JAR file from the [releases page](https://github.com/aguara-guazu/OllamaCraft/releases)
2. Place the JAR file in your server's `plugins` directory
3. Start or restart your server
4. The plugin will generate default configuration files

## Usage

### Chat Commands

- `/ai <message>` - Send a message to the AI
- `/ai ask <message>` - Same as above
- `/ai clear` - Clear conversation history

### Chat Interaction

By default, you can talk to the AI by prefixing your message with "Steve, " in chat:

```
Steve, how do I make a redstone clock?
```

### Configuration Commands

- `/aiconfig` - View current settings
- `/aiconfig <setting> <value>` - Change a setting
- `/aiconfig reload` - Reload configuration from disk

Available settings:
- `model` - The Ollama model to use (e.g., "llama3")
- `temperature` - Response randomness (0.0 to 1.0)
- `max-context-length` - Number of past messages to remember
- `monitor-all-chat` - Whether to monitor all chat messages
- `mcp-enabled` - Toggle MCP integration

## Configuration

The plugin's configuration files are created in the `plugins/OllamaCraft` directory:

### config.yml

```yaml
# Ollama API Settings
ollama:
  api-url: "http://localhost:11434/api"
  model: "llama3"
  system-prompt: "You are Steve, a helpful assistant in a Minecraft world..."
  temperature: 0.7
  max-context-length: 50

# Chat Settings
chat:
  monitor-all-chat: false
  trigger-prefix: "Steve, "
  response-format: "[Steve] &a%message%"

# MCP Integration
mcp:
  enabled: true
  api-url: "http://localhost:25585/mcp"
  api-key: ""
  allowed-commands:
    - "give"
    - "teleport"
    - "time"
    - "weather"
    - "say"
```

## AI Actions

When MCP integration is enabled, the AI can perform actions in the game by detecting commands in its responses:

- Explicit commands: The AI can include commands like `/give player item` in its response
- Implied actions: The AI can say things like "let me give you a diamond" and the plugin will try to execute the appropriate command

## Permissions

- `ollamacraft.ai` - Permission to use /ai commands (default: true)
- `ollamacraft.config` - Permission to use /aiconfig commands (default: op)

## Building from Source

1. Clone this repository
   ```
   git clone https://github.com/aguara-guazu/OllamaCraft.git
   cd OllamaCraft
   ```
2. Build with Maven:
   ```
   mvn clean package
   ```
3. Find the built JAR file in the `target` directory

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Credits

- Uses [Ollama](https://github.com/ollama/ollama) for AI model serving
- Optional integration with [MinecraftMCP](https://github.com/aguara-guazu/MinecraftMCP)