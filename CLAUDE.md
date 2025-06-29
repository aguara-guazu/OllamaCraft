# OllamaCraft - Claude AI Assistant Guide

## Project Overview

**OllamaCraft** is a sophisticated Minecraft PaperMC plugin that integrates AI capabilities into Minecraft servers through the Ollama API. It enables players to interact with an intelligent AI assistant that can understand context, maintain conversations, and autonomously execute Minecraft commands through integrated MCP (Model Context Protocol) tools.

## Tech Stack & Architecture

### Core Technologies
- **Java 21**: Modern Java features and performance
- **PaperMC API 1.21.4**: High-performance Minecraft server platform
- **Maven**: Build automation and dependency management
- **OkHttp 4.12.0**: Robust HTTP client for API communication
- **Gson 2.10.1**: JSON parsing and serialization
- **Ollama API**: AI model serving and inference

### Key Dependencies
```xml
<dependencies>
    <!-- Paper API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- HTTP Client -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>
    
    <!-- JSON Library -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>
</dependencies>
```

## Project Structure

```
src/main/
├── java/com/ollamacraft/plugin/
│   ├── OllamaCraft.java              # Main plugin class
│   ├── AIService.java                # Core AI communication service
│   ├── MCPService.java               # MCP integration service
│   ├── AICommand.java                # /ai command handler
│   ├── AIConfigCommand.java          # /aiconfig command handler
│   ├── ChatListener.java             # Chat event listener
│   ├── MCPBridge.java                # MCP protocol bridge
│   ├── MCPHttpClient.java            # HTTP client for MCP
│   ├── MCPStdioHandler.java          # Stdio handler for MCP
│   ├── MCPToolExecutor.java          # Tool execution engine
│   ├── MCPToolProvider.java          # Tool discovery and caching
│   └── model/
│       ├── ChatMessage.java          # Message data model
│       └── MessageHistory.java       # Thread-safe message history
└── resources/
    ├── plugin.yml                    # Plugin metadata
    └── config.yml                    # Default configuration
```

## Core Components

### 1. Main Plugin Class (`OllamaCraft.java`)
- **Purpose**: Plugin lifecycle management and service coordination
- **Key Features**:
  - Service initialization and cleanup
  - Command registration
  - Event listener setup
  - Graceful shutdown with MCP bridge cleanup

### 2. AI Service (`AIService.java`)
- **Purpose**: Central AI communication and tool orchestration
- **Key Features**:
  - Ollama API integration with tool calling support
  - Conversation history management
  - Tool call processing and response handling
  - Configuration management and hot-reloading
  - Async processing to prevent server blocking

### 3. MCP Integration System
#### MCPService.java
- MCP bridge lifecycle management
- Configuration-driven initialization
- Auto-start capabilities

#### MCPBridge.java
- Pure protocol relay between stdio and HTTP
- Bidirectional message forwarding
- Robust error handling with JSON-RPC responses
- Connection testing and health monitoring

#### MCPHttpClient.java
- Resilient HTTP communication with retry logic
- Comprehensive timeout configuration
- Connection testing and capability detection

#### MCPToolProvider.java
- Tool discovery and caching (1-minute TTL)
- Format conversion from MCP to Ollama tool definitions
- Thread-safe concurrent access

#### MCPToolExecutor.java
- Tool call execution with format adaptation
- 60-second execution timeout
- Comprehensive error handling and result formatting

### 4. Command System
#### AICommand.java
- Main AI interaction command (`/ai`)
- Subcommands: `ask`, `clear`
- Async processing with response broadcasting
- Tab completion support

#### AIConfigCommand.java
- Configuration management (`/aiconfig`)
- Runtime settings modification
- MCP server lifecycle control
- Permission-based access control

### 5. Chat Integration (`ChatListener.java`)
- Chat event monitoring with configurable triggers
- Default trigger: "Steve, " prefix
- Async AI response processing
- Non-intrusive event handling (doesn't cancel original messages)

### 6. Data Models
#### ChatMessage.java
- Simple POJO for role-based messages
- Automatic timestamp generation
- Supports user, assistant, system, and tool roles

#### MessageHistory.java
- Thread-safe message collection with ReadWriteLock
- Configurable maximum history length (default: 50)
- Automatic oldest message removal

## Configuration System

### Main Configuration (`config.yml`)
The plugin uses a comprehensive YAML configuration system with intelligent defaults:

```yaml
# Ollama API Settings
ollama:
  api-url: "http://localhost:11434/api"
  model: "llama3"
  system-prompt: "You are Steve, a helpful assistant in a Minecraft world..."
  temperature: 0.7
  max-context-length: 50

# Chat Integration Settings
chat:
  monitor-all-chat: false
  trigger-prefix: "Steve, "
  response-format: "[Steve] &a%message%"

# MCP Integration
mcp:
  enabled: true
  server:
    url: "http://localhost:25575"
    api-key: "your-secure-api-key-here"
  tools:
    enabled: true
  bridge:
    debug: false
    endpoint: "/mcp"
    timeout-ms: 30000
    retries: 3
    retry-delay-ms: 1000
```

### Plugin Metadata (`plugin.yml`)
```yaml
name: OllamaCraft
version: '${project.version}'
main: com.ollamacraft.plugin.OllamaCraft
api-version: '1.21'
description: A plugin to interact with Ollama-hosted AI model through Minecraft chat

commands:
  ai:
    description: Interact with the AI assistant
    usage: /<command> <message>
    permission: ollamacraft.ai
    aliases: [ask]
  aiconfig:
    description: Configure AI settings
    usage: /<command> <setting> <value>
    permission: ollamacraft.config
    aliases: [aiconf]

permissions:
  ollamacraft.ai:
    description: Allows users to interact with AI
    default: true
  ollamacraft.config:
    description: Allows users to configure AI settings
    default: op
```

## Build System

### Maven Configuration (`pom.xml`)
- **Java Version**: 21 (modern language features)
- **Maven Compiler**: 3.13.0
- **Maven Shade Plugin**: 3.5.2 (dependency bundling)
- **Artifact ID**: `ollamacraft`
- **Group ID**: `com.ollamacraft`

### Build Commands
```bash
# Clean and build
mvn clean package

# Version update
mvn versions:set -DnewVersion=1.0.0

# Dependency analysis
mvn dependency:tree
```

## CI/CD Pipeline

### GitHub Actions Workflows

#### Build Workflow (`.github/workflows/build.yml`)
- **Triggers**: Pull requests to main, manual dispatch
- **Features**:
  - JDK 21 setup with Temurin distribution
  - Maven caching for faster builds
  - Artifact upload for testing
  - Manual release creation with version input

#### Auto Release Workflow (`.github/workflows/auto_release.yml`)
- **Triggers**: Pushes to main branch
- **Features**:
  - Automatic version calculation (minor version increment)
  - Semantic versioning with proper tag management
  - Automated JAR packaging and release creation
  - Comprehensive release notes generation

## Development Workflow

### Common Development Tasks

#### Testing Changes
```bash
# Build and test locally
mvn clean package

# Copy to test server
cp target/ollamacraft-*.jar /path/to/minecraft/plugins/

# Check logs
tail -f /path/to/minecraft/logs/latest.log
```

#### Configuration Testing
```bash
# Test AI connection
/ai hello

# Check MCP status
/aiconfig mcp-status

# View current settings
/aiconfig

# Clear conversation history
/ai clear
```

#### Adding New Features
1. Create feature branch from main
2. Implement changes following existing patterns
3. Test with local Minecraft server
4. Update configuration if needed
5. Create pull request for review

### Code Style Guidelines

#### Architectural Patterns Used
- **Command Pattern**: Separate command classes for different functionalities
- **Bridge Pattern**: MCPBridge acts as protocol translator
- **Adapter Pattern**: Tool executors convert between protocol formats
- **Producer-Consumer**: Async message handling with queues
- **Thread Safety**: Consistent use of locks and atomic operations

#### Best Practices Observed
- Comprehensive logging with configurable debug modes
- Proper resource management with try-with-resources
- Async processing to prevent server blocking
- Configuration-driven behavior with sensible defaults
- Defensive programming with null checks and validation
- Clean separation of concerns between components

## AI Integration Details

### Conversation Flow
1. **Player Message** → Chat listener captures trigger
2. **History Management** → Message added to context
3. **AI Request** → Sent to Ollama with available tools
4. **Tool Execution** → MCP tools called if requested by AI
5. **Response Generation** → Final AI response with tool results
6. **Message Broadcast** → Formatted response sent to players

### Tool Calling Architecture
- **Tool Discovery**: MCPToolProvider caches available tools
- **Format Conversion**: Ollama ↔ MCP protocol adaptation
- **Execution**: Async tool calls with timeout protection
- **Error Handling**: Comprehensive error reporting and recovery

### Supported AI Models
The plugin supports any Ollama model with tool-calling capabilities:
- `llama3` (recommended for general use)
- `llama3:70b` (higher capability, slower)
- `codellama` (code-focused tasks)
- `phi3` (lightweight, faster responses)

## Security Considerations

### API Key Management
- API keys stored in configuration files
- Masked display in status commands
- Secure transmission over HTTPS
- No logging of sensitive credentials

### Permission System
- `ollamacraft.ai`: Basic AI interaction (default: true)
- `ollamacraft.config`: Configuration management (default: op)
- Fine-grained control over tool execution

### Input Validation
- Message content sanitization
- Tool call parameter validation
- Configuration value bounds checking
- Protection against injection attacks

## Performance Optimization

### Async Processing
- All AI requests processed asynchronously
- Non-blocking server operations
- Concurrent tool execution where possible

### Caching Strategy
- Tool definitions cached with 1-minute TTL
- Connection pooling for HTTP requests
- Message history size limiting

### Resource Management
- Proper cleanup on plugin shutdown
- Connection timeout configuration
- Memory-efficient message storage

## Monitoring and Debugging

### Logging Levels
- **INFO**: Normal operation status
- **WARNING**: Recoverable errors and misconfigurations
- **SEVERE**: Critical errors requiring attention
- **DEBUG**: Detailed operation tracing (configurable)

### Health Checks
- MCP bridge connection testing
- Ollama API availability monitoring
- Tool execution status tracking

### Common Issues and Solutions
1. **AI Not Responding**: Check Ollama API URL and model availability
2. **Tools Not Working**: Verify MCP server status and API key
3. **Performance Issues**: Adjust context length and timeout settings
4. **Permission Errors**: Check user permissions and plugin configuration

## Advanced Configuration

### Custom System Prompts
Tailor the AI's personality and capabilities:
```yaml
ollama:
  system-prompt: |
    You are Steve, a helpful Minecraft assistant with the following capabilities:
    - Execute server commands through MCP tools
    - Provide game information and assistance
    - Manage players and world settings
    - Monitor server performance
```

### Tool Execution Control
Fine-tune tool behavior:
```yaml
mcp:
  tools:
    enabled: true
  bridge:
    timeout-ms: 60000    # Longer timeout for complex operations
    retries: 5           # More retries for reliability
    debug: true          # Enable detailed logging
```

### Chat Integration Modes
Configure different interaction styles:
```yaml
chat:
  # Mode 1: Prefix-based (recommended)
  monitor-all-chat: false
  trigger-prefix: "Steve, "
  
  # Mode 2: Monitor all chat (can be noisy)
  monitor-all-chat: true
```

## Troubleshooting Common Issues

### MCP Connection Failures

**Problem**: Plugin shows errors like "Failed to connect to localhost:25575" on startup.

**Solution**: This is expected when no MCP server is running. The plugin will automatically fall back to basic AI chat functionality.

**Quick Fix**:
1. The plugin now defaults to `mcp.enabled: false` in the configuration
2. Basic AI chat works without MCP server
3. To enable MCP tools, set up a separate MCP server first

**Configuration Example**:
```yaml
mcp:
  enabled: false  # Set to true only when MCP server is running
```

### Plugin Startup Issues

If the plugin fails to start completely:
1. Check that Ollama is running and accessible
2. Verify the Ollama API URL in config.yml
3. Ensure the configured AI model is installed in Ollama
4. Check server logs for specific error messages

### AI Not Responding

1. Verify Ollama server is running: `curl http://localhost:11434/api/version`
2. Check if the model exists: `ollama list`
3. Test basic AI command: `/ai hello`
4. Review plugin logs for errors

This guide provides comprehensive information for understanding, developing, and maintaining the OllamaCraft project. The codebase demonstrates professional-grade Java development with modern architectural patterns and robust error handling suitable for production Minecraft servers.