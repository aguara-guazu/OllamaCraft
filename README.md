# OllamaCraft

OllamaCraft is a powerful PaperMC plugin that brings AI assistance directly into your Minecraft server. Players can chat with an intelligent AI assistant powered by Ollama that can understand context, maintain conversations, and autonomously execute Minecraft commands through integrated MCP (Model Context Protocol) tools.

## ✨ Features

### 🤖 **Intelligent AI Assistant**
- Chat with AI directly in Minecraft using natural language
- Maintains conversation history for contextual responses
- Configurable AI models through Ollama
- Smart response formatting and chat integration

### 🛠️ **Autonomous Tool Execution**
- AI can execute Minecraft commands automatically
- Real-time server management through MCP tools
- Player management, world control, and server monitoring
- Intelligent decision-making for when and how to use tools

### 🔧 **Native MCP Bridge**
- Pure Java implementation - no external dependencies
- Automatic tool discovery from MCP servers
- Robust error handling and retry logic
- Zero configuration required

### ⚙️ **Flexible Configuration**
- Comprehensive YAML configuration
- Runtime settings modification through commands
- Fine-grained control over AI behavior and permissions
- Debug logging and monitoring capabilities

## 🚀 Quick Start

### Requirements

- **PaperMC Server**: Version 1.21.4 or newer
- **Java**: Version 21 or newer  
- **Ollama**: Local or remote server with tool calling support
- **MinecraftMCP Server**: For tool functionality (separate installation)

### Installation

1. **Download** the latest JAR from [releases](https://github.com/aguara-guazu/OllamaCraft/releases)
2. **Place** the JAR in your server's `plugins/` directory
3. **Start** your server to generate configuration files
4. **Configure** the plugin (see Configuration section below)
5. **Restart** the server to apply settings

## 📖 Usage Guide

### Basic Chat Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/ai <message>` | Send a message to the AI | `/ai How do I make a redstone clock?` |
| `/ai ask <message>` | Same as above | `/ai ask What's the weather like?` |
| `/ai clear` | Clear conversation history | `/ai clear` |

### Chat Integration

By default, you can talk to the AI by prefixing messages with `"Steve, "` in chat:

```
Steve, can you give me some food? I'm starving!
Steve, what time is it in the game?
Steve, teleport me to spawn please
```

### Configuration Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/aiconfig` | View current settings | `ollamacraft.config` |
| `/aiconfig <setting> <value>` | Change a setting | `ollamacraft.config` |
| `/aiconfig reload` | Reload configuration | `ollamacraft.config` |

### MCP Management Commands

| Command | Description |
|---------|-------------|
| `/aiconfig mcp-start` | Start the MCP bridge |
| `/aiconfig mcp-stop` | Stop the MCP bridge |
| `/aiconfig mcp-restart` | Restart the MCP bridge |
| `/aiconfig mcp-status` | View MCP bridge status |

### Available Settings

| Setting | Description | Example Values |
|---------|-------------|----------------|
| `model` | Ollama model to use | `llama3`, `codellama`, `mistral` |
| `temperature` | Response creativity (0.0-1.0) | `0.7`, `0.9`, `0.3` |
| `max-context-length` | Messages to remember | `50`, `100`, `25` |
| `monitor-all-chat` | Monitor all chat messages | `true`, `false` |
| `mcp-enabled` | Toggle MCP integration | `true`, `false` |

## ⚙️ Configuration

### Main Configuration (`config.yml`)

```yaml
# Ollama API Settings
ollama:
  # The URL of your Ollama server
  api-url: "http://localhost:11434/api"
  
  # AI model to use (must support tool calling for MCP features)
  model: "llama3"
  
  # System prompt - defines the AI's personality and capabilities
  system-prompt: "You are Steve, a helpful assistant in a Minecraft world. You have access to Minecraft tools through MCP that allow you to execute commands and interact with the server."
  
  # Temperature controls randomness (0.0 = deterministic, 1.0 = very creative)
  temperature: 0.7
  
  # Number of previous messages to remember for context
  max-context-length: 50

# Chat Integration Settings
chat:
  # Monitor all chat messages for AI triggers
  monitor-all-chat: false
  
  # Prefix that triggers AI responses when monitor-all-chat is false
  trigger-prefix: "Steve, "
  
  # How AI responses are formatted (%message% = AI response)
  response-format: "[Steve] &a%message%"

# MCP (Model Context Protocol) Integration
mcp:
  # Enable MCP integration
  enabled: true
  
  # MCP server configuration
  server:
    # URL of your MinecraftMCP server
    url: "http://localhost:25575"
    
    # API key for authentication (keep secure!)
    api-key: "your-secure-api-key-here"
  
  # MCP tools configuration
  tools:
    # Allow the AI to use MCP tools autonomously
    enabled: true
  
  # MCP bridge configuration
  bridge:
    # Enable debug logging for troubleshooting
    debug: false
    
    # HTTP endpoint path
    endpoint: "/mcp"
    
    # Request timeout in milliseconds
    timeout-ms: 30000
    
    # Number of retry attempts for failed requests
    retries: 3
    
    # Delay between retries in milliseconds
    retry-delay-ms: 1000
```

### Setting Up MinecraftMCP Server

To enable AI tool functionality, you need a MinecraftMCP server. Here are your options:

#### Option 1: MinecraftMCP Paper Plugin
1. Download the MinecraftMCP plugin
2. Install it on your server
3. Configure it with an API key
4. Set the same API key in OllamaCraft's config

#### Option 2: External MinecraftMCP Server
1. Run a separate MinecraftMCP server
2. Point OllamaCraft to its URL
3. Configure authentication

## 🎯 AI Tool Examples

When MCP integration is enabled, the AI can autonomously use Minecraft tools:

### Server Management

```
Player: "How's the server performing?"
AI: [calls minecraft_server_status]
AI: "The server is running well! TPS is 19.8, memory usage is at 45%, and there are 12 players online."
```

### Player Management

```
Player: "Who else is playing right now?"
AI: [calls minecraft_player_list]
AI: "There are currently 3 other players online: Alice (Creative mode), Bob (Survival), and Charlie (Adventure mode)."
```

### World Control

```
Player: "It's too dark, can you make it daytime?"
AI: [calls minecraft_execute_command with "/time set day"]
AI: "I've set the time to day! The sun should be shining now."
```

### Item Management

```
Player: "I need some food, I'm starving!"
AI: [calls minecraft_execute_command with "/give player bread 16"]
AI: "I've given you 16 bread! That should keep you fed for a while."
```

### Teleportation

```
Player: "Can you teleport me to spawn?"
AI: [calls minecraft_manage_player with teleport action]
AI: "I've teleported you to spawn! You should be at the server spawn point now."
```

### Server Logs

```
Player: "Have there been any errors recently?"
AI: [calls minecraft_server_logs with error filter]
AI: "I checked the recent logs and found 2 minor warnings but no critical errors. The server is running smoothly!"
```

## 🔧 Advanced Configuration

### Ollama Model Selection

Different models offer different capabilities:

```yaml
# For general chat and basic tool usage
model: "llama3"

# For more creative responses
model: "llama3:70b"

# For code-focused tasks
model: "codellama"

# For fast responses (less capable)
model: "phi3"
```

### Temperature Settings

```yaml
# Very deterministic responses
temperature: 0.1

# Balanced (recommended)
temperature: 0.7

# Creative and varied responses
temperature: 0.9
```

### Chat Monitoring Modes

```yaml
# Mode 1: Trigger-based (recommended)
monitor-all-chat: false
trigger-prefix: "Steve, "

# Mode 2: Monitor everything (can be noisy)
monitor-all-chat: true
trigger-prefix: ""  # Not used in this mode
```

### Response Formatting

```yaml
# Simple format
response-format: "&a[AI] %message%"

# With colors and styling
response-format: "&e&l[Steve] &r&a%message%"

# Minimal format
response-format: "%message%"
```

### MCP Bridge Settings

```yaml
# Native Java bridge - fully integrated, no external dependencies
bridge:
  debug: false          # Enable for troubleshooting
  timeout-ms: 30000     # Adjust for network conditions
  retries: 3            # Number of retry attempts
```

## 🛡️ Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `ollamacraft.ai` | Use `/ai` commands | `true` |
| `ollamacraft.config` | Use `/aiconfig` commands | `op` |

### Permission Examples

```yaml
# Give all players AI access
permissions:
  ollamacraft.ai: true

# Restrict configuration to staff
groups:
  staff:
    permissions:
      - ollamacraft.config
```

## 🔍 Troubleshooting

### Common Issues

#### AI Not Responding
```yaml
# Check Ollama connection
ollama:
  api-url: "http://localhost:11434/api"  # Verify this URL

# Check model availability
model: "llama3"  # Ensure this model is installed in Ollama
```

#### Tools Not Working
```yaml
# Verify MCP configuration
mcp:
  enabled: true
  tools:
    enabled: true
  server:
    url: "http://localhost:25575"      # Check MinecraftMCP server is running
    api-key: "correct-api-key-here"    # Verify API key matches
```

#### Bridge Connection Issues
```yaml
# Enable debug logging
mcp:
  bridge:
    debug: true

# Check timeout settings
    timeout-ms: 60000  # Increase if needed
    retries: 5         # Increase retry attempts
```

### Debug Commands

```bash
# Check MCP status
/aiconfig mcp-status

# View current configuration
/aiconfig

# Test AI response
/ai hello

# Clear conversation if stuck
/ai clear
```

### Log Analysis

Look for these log messages:

```
[INFO] MCP tools integration initialized          # ✅ Tools working
[INFO] MCP bridge started successfully           # ✅ Bridge working
[WARNING] MCP service not running                # ❌ Check MCP server
[ERROR] Failed to communicate with MCP server    # ❌ Check connection
```

## 🏗️ Architecture

### Component Overview

```
┌─────────────┐    Chat     ┌─────────────┐    Tool Calls   ┌─────────────┐    HTTP     ┌─────────────┐
│   Player    │────────────►│ OllamaCraft │◄───────────────►│ Ollama AI   │────────────►│ MCP Server  │
│             │             │   Plugin    │                 │   Model     │             │             │
└─────────────┘             └─────────────┘                 └─────────────┘             └─────────────┘
```

### Key Components

- **AIService**: Manages Ollama communication and tool calling
- **MCPToolProvider**: Discovers and caches MCP tools
- **MCPToolExecutor**: Executes tool calls and handles responses
- **MCPHttpClient**: HTTP communication with MCP servers
- **MCPBridge**: stdio/HTTP bridge for Claude Desktop integration

### Data Flow

1. **Player sends message** → Chat listener captures it
2. **Message processed** → Added to conversation history
3. **AI request sent** → Includes available tools
4. **AI responds** → May include tool calls
5. **Tools executed** → Results returned to AI
6. **Final response** → Sent back to player

## 🚀 Performance Tips

### Optimization Settings

```yaml
# Reduce context for better performance
ollama:
  max-context-length: 25

# Cache tools for better response time
mcp:
  tools:
    enabled: true  # Tools are cached automatically

# Adjust timeouts for your network
mcp:
  bridge:
    timeout-ms: 15000  # Faster timeout for local servers
    retries: 2         # Fewer retries for speed
```

### Model Selection for Performance

```yaml
# Fastest (basic capabilities)
model: "phi3"

# Balanced speed/capability
model: "llama3"

# Best capability (slower)
model: "llama3:70b"
```

## 🔗 Integration Examples

### With Other Plugins

OllamaCraft can work alongside other plugins:

```yaml
# Example: Integration with economy plugin
# AI can check player balances, give money, etc.
# (requires appropriate MCP tools)

# Example: Integration with permissions plugin
# AI can manage player permissions
# (requires appropriate MCP tools)
```

### Custom MCP Tools

You can extend functionality by creating custom MCP tools:

```yaml
# The AI will automatically discover and use new tools
# as they're added to your MCP server
```

## 📝 Building from Source

```bash
# Clone the repository
git clone https://github.com/aguara-guazu/OllamaCraft.git
cd OllamaCraft

# Build with Maven
mvn clean package

# Find the JAR in target/ directory
ls target/ollamacraft-*.jar
```

## 🤝 Contributing

We welcome contributions! Here's how to get started:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Setup

```bash
# Clone your fork
git clone https://github.com/your-username/OllamaCraft.git

# Set up development environment
cd OllamaCraft
mvn clean compile

# Run tests (when available)
mvn test
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- **[Ollama](https://github.com/ollama/ollama)** - AI model serving platform
- **[MinecraftMCP](https://github.com/aguara-guazu/MinecraftMCP)** - MCP server implementation
- **[PaperMC](https://papermc.io/)** - High-performance Minecraft server

## 🆘 Support

- **Issues**: [GitHub Issues](https://github.com/aguara-guazu/OllamaCraft/issues)
- **Discussions**: [GitHub Discussions](https://github.com/aguara-guazu/OllamaCraft/discussions)
- **Wiki**: [Project Wiki](https://github.com/aguara-guazu/OllamaCraft/wiki)

---

Made with ❤️ for the Minecraft community