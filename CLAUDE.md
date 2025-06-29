# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# OllamaCraft - Claude AI Assistant Guide

## Project Overview

**OllamaCraft** is a sophisticated Minecraft PaperMC plugin that integrates multiple AI providers into Minecraft servers. It features a multi-provider architecture supporting Ollama, Claude (Anthropic), and OpenAI, enabling players to interact with intelligent AI assistants that can understand context, maintain conversations, and autonomously execute Minecraft commands through integrated MCP (Model Context Protocol) tools.

## Tech Stack & Architecture

### Core Technologies
- **Java 21**: Modern Java features and performance
- **PaperMC API 1.21.4**: High-performance Minecraft server platform
- **Maven**: Build automation and dependency management
- **OkHttp 4.12.0**: Robust HTTP client for API communication
- **Gson 2.10.1**: JSON parsing and serialization
- **Multiple AI Providers**: Ollama, Claude (Anthropic), OpenAI API integration

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
│   ├── AIService.java                # Legacy AI service (Ollama-only)
│   ├── AIServiceV2.java              # Modern multi-provider AI service
│   ├── MCPService.java               # MCP integration service
│   ├── AICommand.java                # /ai command handler
│   ├── AIConfigCommand.java          # /aiconfig command handler
│   ├── ChatListener.java             # Chat event listener
│   ├── ResponseDetectionService.java # Intelligent chat response detection
│   ├── MCPBridge.java                # MCP protocol bridge
│   ├── MCPHttpClient.java            # HTTP client for MCP
│   ├── MCPStdioHandler.java          # Stdio handler for MCP
│   ├── MCPToolExecutor.java          # Tool execution engine
│   ├── MCPToolProvider.java          # Tool discovery and caching
│   ├── config/
│   │   └── AIConfiguration.java      # Advanced configuration management
│   ├── provider/
│   │   ├── AIProvider.java           # Provider interface
│   │   ├── AIProviderFactory.java    # Provider factory and caching
│   │   ├── BaseAIProvider.java       # Common provider functionality
│   │   ├── OllamaProvider.java       # Ollama integration
│   │   ├── ClaudeProvider.java       # Anthropic Claude integration
│   │   └── OpenAIProvider.java       # OpenAI integration
│   └── model/
│       ├── ChatMessage.java          # Message data model
│       ├── MessageHistory.java       # Thread-safe message history
│       ├── AIResponse.java           # Unified AI response model
│       └── ToolCall.java             # Universal tool call representation
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

### 2. AI Architecture (Multi-Provider System)
#### AIServiceV2.java (Modern Implementation)
- **Purpose**: Multi-provider AI communication and orchestration
- **Key Features**:
  - Support for multiple AI providers (Ollama, Claude, OpenAI)
  - Intelligent response detection system
  - Provider-agnostic tool calling with format conversion
  - Runtime provider switching and configuration hot-reloading
  - Advanced error handling with provider fallbacks
  - Async processing to prevent server blocking

#### AIService.java (Legacy Implementation)
- **Purpose**: Original Ollama-only AI communication
- **Status**: Maintained for backward compatibility
- **Key Features**:
  - Direct Ollama API integration
  - Basic tool calling support
  - Conversation history management

### 3. Multi-Provider Architecture
#### Provider System (provider/ package)
- **AIProvider Interface**: Unified interface for all AI providers
  - `chat()`: Basic chat functionality
  - `chatWithTools()`: Chat with tool calling support
  - `testConnection()`: Provider health checking
- **AIProviderFactory**: Factory pattern for provider creation and caching
- **Provider Implementations**:
  - `OllamaProvider`: Local Ollama server integration
  - `ClaudeProvider`: Anthropic Claude API integration
  - `OpenAIProvider`: OpenAI API integration
- **Tool Conversion System**: Provider-specific tool format conversion
- **Configuration Management**: Per-provider settings and validation

#### Intelligent Response Detection
- **ResponseDetectionService**: AI-powered chat response detection
- **Multi-tier Detection**: Pattern matching + AI-based contextual analysis
- **Configurable Detection Provider**: Use different AI provider for detection
- **Confidence Scoring**: 0.0-1.0 confidence scores for response decisions

### 4. MCP Integration System
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

### 5. Command System
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

### 6. Chat Integration (`ChatListener.java`)
- Chat event monitoring with configurable triggers
- Default trigger: "Steve, " prefix
- Async AI response processing
- Non-intrusive event handling (doesn't cancel original messages)

### 7. Data Models
#### ChatMessage.java
- Simple POJO for role-based messages
- Automatic timestamp generation
- Supports user, assistant, system, and tool roles

#### MessageHistory.java
- Thread-safe message collection with ReadWriteLock
- Configurable maximum history length (default: 50)
- Automatic oldest message removal

#### AIResponse.java (Multi-Provider)
- Unified response model across all AI providers
- Success/failure status tracking
- Tool call extraction and processing
- Error handling and reporting

#### ToolCall.java
- Universal tool call representation
- Provider-agnostic tool format
- MCP protocol integration
- Parameter validation and conversion

## Configuration System

### Modern Configuration (`config.yml`)
The plugin uses a comprehensive multi-provider YAML configuration system:

```yaml
# Multi-Provider AI Settings
ai:
  provider: "ollama"              # Primary provider: ollama, claude, openai
  agent-name: "Steve"             # Configurable agent name
  system-prompt: "You are {agent_name}, a helpful assistant in a Minecraft world..."
  temperature: 0.7
  max-context-length: 50

# Provider-specific configurations
ollama:
  model: "llama3.1"
  base-url: "http://localhost:11434/api"
  timeout-seconds: 60
  max-retries: 3

claude:
  model: "claude-3-5-sonnet-20241022"
  api-key: "your-claude-api-key"
  base-url: "https://api.anthropic.com/v1"
  timeout-seconds: 60
  max-retries: 3

openai:
  model: "gpt-4o"
  api-key: "your-openai-api-key"
  base-url: "https://api.openai.com/v1"
  organization: "your-org-id"
  timeout-seconds: 60
  max-retries: 3

# Enhanced Chat Integration
chat:
  monitor-all-chat: false
  trigger-prefix: "Steve, "
  response-format: "[Steve] &a%message%"
  detection:
    intelligent-detection: true    # AI-powered response detection
    fallback-to-prefix: true      # Fallback to prefix matching
    detection-provider: "ollama"   # Provider for detection AI
    detection-timeout-seconds: 5

# MCP Integration
mcp:
  enabled: false                  # Default disabled for basic functionality
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

# Startup Integration Test
startup-test:
  enabled: true
  delay-seconds: 5
  broadcast-to-players: true
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

# Check current provider status
/aiconfig provider-status

# Switch AI provider (runtime)
/aiconfig set-provider claude

# Test provider connection
/aiconfig test-connection ollama

# Check MCP status
/aiconfig mcp-status

# View current settings
/aiconfig

# Clear conversation history
/ai clear

# Toggle intelligent detection
/aiconfig set chat.detection.intelligent-detection true
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

### Architectural Evolution

#### Migration from Single to Multi-Provider
The project has evolved from a single-provider (Ollama-only) architecture to a sophisticated multi-provider system:

**Legacy Architecture (AIService):**
- Direct Ollama API integration
- Simple tool calling
- Basic configuration management

**Modern Architecture (AIServiceV2):**
- Multi-provider support (Ollama, Claude, OpenAI)
- Intelligent response detection
- Provider-agnostic tool calling
- Advanced configuration with validation
- Runtime provider switching

#### Backward Compatibility
- Both AIService and AIServiceV2 coexist
- Gradual migration path for existing installations
- Configuration supports both legacy and modern formats
- Feature flags for selective enablement

## AI Integration Details

### Startup Integration Test

The plugin includes an automatic startup test that verifies AI and MCP functionality:

**Features:**
- **Automatic Greeting**: AI introduces itself when server starts
- **MCP Tools Discovery**: Reports available tools and capabilities
- **Full Integration Test**: Tests actual AI communication and tool calling
- **Configurable Timing**: Adjustable delay to ensure server is fully ready
- **Broadcasting Options**: Can announce to all players or log-only

**Configuration:**
```yaml
startup-test:
  enabled: true              # Enable/disable startup test
  delay-seconds: 5           # Wait time before running test
  broadcast-to-players: true # Announce to players vs log-only
```

**What the AI Will Report:**
- Successful connection to Ollama
- Available MCP tools (if MCP server is running)
- Current operational mode (basic chat vs full tool access)
- Friendly introduction for players

**Example Output:**
```
[Steve] Good morning! The server has just started and I'm ready to help! I currently have access to 8 MCP tools including server management, player commands, and world control. Feel free to ask me anything or request assistance with your Minecraft adventures!
```

### Conversation Flow (Multi-Provider)
1. **Player Message** → Chat listener captures message
2. **Intelligent Detection** → AI-powered response detection (if enabled)
3. **Provider Selection** → Choose appropriate AI provider
4. **History Management** → Message added to conversation context
5. **Tool Discovery** → Available MCP tools identified and converted
6. **AI Request** → Sent to selected provider with provider-specific tool format
7. **Tool Execution** → MCP tools called if requested by AI
8. **Response Processing** → AI response converted to unified format
9. **Message Broadcast** → Formatted response sent to players

### Legacy Conversation Flow (AIService)
1. **Player Message** → Chat listener captures trigger
2. **History Management** → Message added to context
3. **AI Request** → Sent to Ollama with available tools
4. **Tool Execution** → MCP tools called if requested by AI
5. **Response Generation** → Final AI response with tool results
6. **Message Broadcast** → Formatted response sent to players

### Tool Calling Architecture (Multi-Provider)
- **Tool Discovery**: MCPToolProvider caches available tools (1-minute TTL)
- **Format Conversion**: Provider-specific tool format conversion
  - `OllamaToolConverter`: Ollama format conversion
  - `ClaudeToolConverter`: Anthropic Claude format conversion  
  - `OpenAIToolConverter`: OpenAI format conversion
- **Unified Tool Calls**: `ToolCall` model for provider-agnostic execution
- **MCP Execution**: Async tool calls with 60-second timeout protection
- **Error Handling**: Comprehensive error reporting with provider fallbacks

### Legacy Tool Calling Architecture
- **Tool Discovery**: MCPToolProvider caches available tools
- **Format Conversion**: Ollama ↔ MCP protocol adaptation
- **Execution**: Async tool calls with timeout protection
- **Error Handling**: Comprehensive error reporting and recovery

### Supported AI Models and Providers

#### Ollama Models
Any Ollama model with tool-calling capabilities:
- `llama3.1` (recommended for general use)
- `llama3:70b` (higher capability, slower)
- `codellama` (code-focused tasks)
- `phi3` (lightweight, faster responses)

#### Claude (Anthropic) Models
- `claude-3-5-sonnet-20241022` (recommended, excellent tool calling)
- `claude-3-5-haiku-20241022` (faster, cost-effective)
- `claude-3-opus-20240229` (highest capability, slower)

#### OpenAI Models
- `gpt-4o` (recommended, excellent tool calling)
- `gpt-4o-mini` (faster, cost-effective)
- `gpt-4-turbo` (high capability)
- `gpt-3.5-turbo` (basic functionality, limited tool calling)

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