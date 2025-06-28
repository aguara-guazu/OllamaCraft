package com.ollamacraft.plugin.model;

/**
 * Represents a chat message in the AI conversation
 */
public class ChatMessage {
    
    private String role;
    private String content;
    private long timestamp;
    
    /**
     * Create a new chat message
     * @param role The role of the sender (user, assistant, system)
     * @param content The message content
     */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get the role of the sender
     * @return The role (user, assistant, system)
     */
    public String getRole() {
        return role;
    }
    
    /**
     * Set the role of the sender
     * @param role The role (user, assistant, system)
     */
    public void setRole(String role) {
        this.role = role;
    }
    
    /**
     * Get the message content
     * @return The message content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Set the message content
     * @param content The message content
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * Get the message timestamp
     * @return The timestamp when the message was created
     */
    public long getTimestamp() {
        return timestamp;
    }
}