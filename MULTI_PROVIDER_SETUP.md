# OllamaCraft - Configuración Multi-Proveedor

Este documento explica cómo configurar y usar el nuevo sistema multi-proveedor de OllamaCraft que soporta Ollama, Anthropic Claude y OpenAI.

## 🎯 Características Principales

- **Múltiples Proveedores**: Soporte para Ollama (local), Claude (Anthropic) y OpenAI
- **Selección de Modelo**: Configura cualquier modelo de cada proveedor
- **Detección Inteligente**: El agente decide cuándo responder usando IA contextual
- **Nombre Configurable**: Personaliza el nombre de tu agente
- **Historial Unificado**: Mantiene contexto entre conversaciones
- **Herramientas MCP**: Integración completa con todas las herramientas

## 📝 Configuración Completa

### 1. Configuración Básica del Agente

```yaml
ai:
  provider: "claude"          # Opciones: "ollama", "claude", "openai"
  agent-name: "Steve"         # Nombre personalizable del agente
  system-prompt: "Eres {agent_name}, un asistente útil en un mundo de Minecraft que puede ayudar a los jugadores con comandos, construcción, crafting y administración del servidor."
  temperature: 0.7            # Creatividad del modelo (0.0-1.0)
  max-context-length: 50      # Máximo mensajes en historial
```

### 2. Configuración por Proveedor

#### Ollama (Local)
```yaml
ollama:
  model: "llama3.1"                    # Modelo local de Ollama
  base-url: "http://localhost:11434/api"
  timeout-seconds: 60
  max-retries: 3
```

**Modelos recomendados de Ollama:**
- `llama3.1` - Uso general (recomendado)
- `llama3:70b` - Mayor capacidad, más lento
- `codellama` - Especializado en código
- `phi3` - Rápido y ligero

#### Anthropic Claude
```yaml
claude:
  model: "claude-3-5-sonnet-20241022"   # Modelo de Claude
  api-key: "sk-ant-api03-..."           # Tu API key de Anthropic
  base-url: "https://api.anthropic.com/v1"
  timeout-seconds: 60
  max-retries: 3
```

**Modelos disponibles de Claude:**
- `claude-3-5-sonnet-20241022` - Excelente tool calling (recomendado)
- `claude-3-5-haiku-20241022` - Rápido y económico
- `claude-3-opus-20240229` - Máxima capacidad
- `claude-sonnet-4-20250514` - Último modelo Claude 4 (beta)
- `claude-opus-4-20250514` - Claude 4 más avanzado (beta)

#### OpenAI
```yaml
openai:
  model: "gpt-4o"                      # Modelo de OpenAI
  api-key: "sk-..."                    # Tu API key de OpenAI
  base-url: "https://api.openai.com/v1"
  organization: "org-..."              # Opcional: ID de organización
  timeout-seconds: 60
  max-retries: 3
```

**Modelos disponibles de OpenAI:**
- `gpt-4o` - Última versión GPT-4 (recomendado)
- `gpt-4o-mini` - Versión rápida y económica
- `gpt-4-turbo` - Versión turbo de GPT-4
- `gpt-3.5-turbo` - Económico para uso básico

### 3. Sistema de Detección Inteligente

```yaml
chat:
  monitor-all-chat: false              # Monitor todo el chat vs solo menciones
  trigger-prefix: "Steve, "            # Prefijo tradicional de activación
  response-format: "[Steve] &a%message%"
  
  detection:
    intelligent-detection: true        # Habilitar detección con IA
    detection-provider: "claude"       # Proveedor para análisis contextual
    detection-timeout-seconds: 3       # Timeout para análisis IA
    confidence-threshold: 0.6          # Mínimo confidence para responder
    fallback-to-prefix: true          # Usar prefijo si falla la IA
    cache-decisions: true             # Cache para mejor rendimiento
    cache-duration-minutes: 5         # Duración del cache
```

### 4. Integración MCP (Opcional)

```yaml
mcp:
  enabled: false                      # Habilitar solo si tienes servidor MCP
  server:
    url: "http://localhost:25575"     # URL del servidor MCP
    api-key: "your-secure-api-key"    # API key del servidor MCP
  tools:
    enabled: true                     # Habilitar herramientas
  bridge:
    debug: false
    timeout-ms: 30000
    retries: 3
```

### 5. Test de Inicio

```yaml
startup-test:
  enabled: true                       # Test automático al iniciar
  delay-seconds: 5                    # Espera antes del test
  broadcast-to-players: true          # Anunciar a jugadores conectados
```

## 🚀 Configuración Rápida

### Para empezar con Claude (Recomendado)
```yaml
ai:
  provider: "claude"
  agent-name: "Assistant"

claude:
  model: "claude-3-5-sonnet-20241022"
  api-key: "TU_API_KEY_AQUI"

chat:
  detection:
    intelligent-detection: true
    detection-provider: "claude"
```

### Para empezar con OpenAI
```yaml
ai:
  provider: "openai"
  agent-name: "ChatGPT"

openai:
  model: "gpt-4o"
  api-key: "TU_API_KEY_AQUI"

chat:
  detection:
    intelligent-detection: true
    detection-provider: "openai"
```

### Para usar solo Ollama (Local)
```yaml
ai:
  provider: "ollama"
  agent-name: "Llama"

ollama:
  model: "llama3.1"

chat:
  detection:
    intelligent-detection: true
    detection-provider: "ollama"
```

## 🔧 Funcionamiento del Sistema

### Detección Inteligente

El sistema analiza cada mensaje del chat para decidir si el agente debe responder:

1. **Análisis rápido** (1ms): Patrones como preguntas, saludos, menciones
2. **Análisis contextual** (100ms-3s): Usa IA para analizar contexto y intención
3. **Decisión final**: Combina ambos análisis con confidence score

### Ejemplos de Detección

**✅ Respondería a:**
- "¿Cómo hago una espada de diamante?"
- "Assistant, help me with redstone"
- "I'm confused about this build"
- "Hola, necesito ayuda"

**❌ No respondería a:**
- "lol that's funny" (conversación casual)
- "/tp spawn" (comando)
- "hey everyone what's up" (saludo general)

### Historial y Contexto

- Mantiene historial de conversación por jugador
- El agente recuerda interacciones previas
- Contexto compartido entre todos los proveedores
- Cache inteligente para mejor rendimiento

## 🛡️ Seguridad

- API keys nunca se muestran en logs
- Validación de entrada para prevenir inyección
- Timeouts configurables para evitar bloqueos
- Fallbacks automáticos si un proveedor falla

## 🔍 Troubleshooting

### El agente no responde
1. Verificar que `intelligent-detection: true`
2. Verificar API key del proveedor
3. Verificar `confidence-threshold` (bajar a 0.4 para testing)
4. Revisar logs del servidor

### Errores de API
1. Verificar conectividad a internet
2. Verificar validez de API keys
3. Verificar quotas/límites de rate de la API
4. Revisar configuración de `timeout-seconds`

### Performance Issues
1. Habilitar `cache-decisions: true`
2. Ajustar `detection-timeout-seconds`
3. Usar modelo más rápido para detección
4. Configurar `fallback-to-prefix: true`

## 📞 Obtener API Keys

### Anthropic Claude
1. Visita https://console.anthropic.com/
2. Crea una cuenta
3. Genera una API key en "Account Settings"
4. Formato: `sk-ant-api03-...`

### OpenAI
1. Visita https://platform.openai.com/
2. Crea una cuenta  
3. Genera una API key en "API Keys"
4. Formato: `sk-...`

### Ollama (Local)
1. Instala Ollama: https://ollama.ai/
2. Ejecuta: `ollama pull llama3.1`
3. Inicia el servidor: `ollama serve`
4. No requiere API key

## 🎉 ¡Listo!

Tu servidor Minecraft ahora tiene un asistente IA inteligente que puede:
- Responder preguntas contextuales
- Ayudar con comandos y crafting
- Ejecutar herramientas del servidor (con MCP)
- Mantener conversaciones naturales
- Actuar solo cuando es necesario

¡Disfruta de tu nuevo asistente IA!