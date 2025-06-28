package com.ollamacraft.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * stdin/stdout handler for MCP protocol communication
 */
public class MCPStdioHandler {
    
    private final boolean debug;
    private final Consumer<JsonObject> onMessage;
    private final Logger logger;
    private final Gson gson;
    private final AtomicBoolean isRunning;
    private final BlockingQueue<JsonObject> outputQueue;
    
    private Thread inputThread;
    private Thread outputThread;
    private BufferedReader stdinReader;
    private PrintWriter stdoutWriter;
    
    /**
     * Create a new stdio handler for MCP communication
     * @param options Configuration options
     */
    public MCPStdioHandler(MCPStdioHandlerOptions options) {
        this.debug = options.debug;
        this.onMessage = options.onMessage;
        this.logger = options.logger;
        this.gson = new Gson();
        this.isRunning = new AtomicBoolean(false);
        this.outputQueue = new LinkedBlockingQueue<>();
        
        // Initialize stdio streams
        this.stdinReader = new BufferedReader(new InputStreamReader(System.in));
        this.stdoutWriter = new PrintWriter(new OutputStreamWriter(System.out), true);
        
        debug("stdin/stdout handler initialized");
    }
    
    /**
     * Log debug message if debug mode is enabled
     */
    private void debug(String message) {
        if (debug && logger != null) {
            logger.info("[MCPStdioHandler] " + message);
        }
    }
    
    /**
     * Log debug message with data if debug mode is enabled
     */
    private void debug(String message, Object data) {
        if (debug && logger != null) {
            logger.info("[MCPStdioHandler] " + message + ": " + gson.toJson(data));
        }
    }
    
    /**
     * Start the stdio handler
     */
    public void start() {
        if (isRunning.get()) {
            debug("Handler is already running");
            return;
        }
        
        isRunning.set(true);
        
        // Start input thread to read from stdin
        inputThread = new Thread(this::handleInput, "MCP-StdioInput");
        inputThread.setDaemon(true);
        inputThread.start();
        
        // Start output thread to write to stdout
        outputThread = new Thread(this::handleOutput, "MCP-StdioOutput");
        outputThread.setDaemon(true);
        outputThread.start();
        
        debug("Handler started");
    }
    
    /**
     * Stop the stdio handler
     */
    public void stop() {
        if (!isRunning.get()) {
            debug("Handler is not running");
            return;
        }
        
        debug("Stopping handler");
        isRunning.set(false);
        
        // Interrupt threads
        if (inputThread != null) {
            inputThread.interrupt();
        }
        if (outputThread != null) {
            outputThread.interrupt();
        }
        
        // Close streams
        try {
            if (stdinReader != null) {
                stdinReader.close();
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "[MCPStdioHandler] Error closing stdin reader", e);
            }
        }
        
        if (stdoutWriter != null) {
            stdoutWriter.close();
        }
        
        debug("Handler stopped");
    }
    
    /**
     * Send a message to stdout
     * @param message Message to send
     */
    public void sendMessage(JsonObject message) {
        if (!isRunning.get()) {
            debug("Handler is not running, cannot send message");
            return;
        }
        
        try {
            outputQueue.offer(message);
            debug("Queued message for stdout", message);
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.WARNING, "[MCPStdioHandler] Error queuing message for stdout", e);
            }
        }
    }
    
    /**
     * Handle input from stdin
     */
    private void handleInput() {
        debug("Input thread started");
        
        try {
            String line;
            while (isRunning.get() && (line = stdinReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    // Parse the line as JSON
                    JsonObject message = gson.fromJson(line, JsonObject.class);
                    debug("Received message from stdin", message);
                    
                    // If a message handler is registered, call it
                    if (onMessage != null) {
                        onMessage.accept(message);
                    }
                } catch (JsonSyntaxException e) {
                    if (logger != null) {
                        logger.log(Level.WARNING, "[MCPStdioHandler] Error parsing stdin message: " + e.getMessage());
                        logger.log(Level.WARNING, "[MCPStdioHandler] Raw message: " + line);
                    }
                }
            }
        } catch (IOException e) {
            if (isRunning.get() && logger != null) {
                logger.log(Level.WARNING, "[MCPStdioHandler] Error reading from stdin", e);
            }
        } finally {
            debug("Input thread ended");
        }
    }
    
    /**
     * Handle output to stdout
     */
    private void handleOutput() {
        debug("Output thread started");
        
        try {
            while (isRunning.get()) {
                try {
                    // Wait for messages to send
                    JsonObject message = outputQueue.take();
                    
                    if (message != null) {
                        String serializedMessage = gson.toJson(message);
                        debug("Sending message to stdout", message);
                        
                        stdoutWriter.println(serializedMessage);
                        stdoutWriter.flush();
                    }
                } catch (InterruptedException e) {
                    // Thread was interrupted, likely during shutdown
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            if (isRunning.get() && logger != null) {
                logger.log(Level.WARNING, "[MCPStdioHandler] Error in output thread", e);
            }
        } finally {
            debug("Output thread ended");
        }
    }
    
    /**
     * Check if the handler is running
     * @return True if running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Configuration options for MCPStdioHandler
     */
    public static class MCPStdioHandlerOptions {
        public boolean debug = false;
        public Consumer<JsonObject> onMessage;
        public Logger logger;
        
        public MCPStdioHandlerOptions() {
        }
        
        public MCPStdioHandlerOptions debug(boolean debug) {
            this.debug = debug;
            return this;
        }
        
        public MCPStdioHandlerOptions onMessage(Consumer<JsonObject> onMessage) {
            this.onMessage = onMessage;
            return this;
        }
        
        public MCPStdioHandlerOptions logger(Logger logger) {
            this.logger = logger;
            return this;
        }
    }
}