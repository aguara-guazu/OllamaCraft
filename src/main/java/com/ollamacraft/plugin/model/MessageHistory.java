package com.ollamacraft.plugin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages the history of chat messages
 */
public class MessageHistory {
    
    private final List<ChatMessage> messages;
    private final ReadWriteLock lock;
    private int maxHistoryLength;
    
    /**
     * Create a new message history manager
     */
    public MessageHistory() {
        this.messages = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.maxHistoryLength = 50; // Default value
    }
    
    /**
     * Add a message to the history
     * @param message The message to add
     */
    public void addMessage(ChatMessage message) {
        lock.writeLock().lock();
        try {
            messages.add(message);
            
            // Remove oldest messages if exceeding max length
            while (messages.size() > maxHistoryLength) {
                messages.remove(0);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get all messages in the history
     * @return A copy of the message list
     */
    public List<ChatMessage> getMessages() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(messages);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear all messages from history
     */
    public void clearHistory() {
        lock.writeLock().lock();
        try {
            messages.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Set the maximum number of messages to keep in history
     * @param maxHistoryLength The maximum history length
     */
    public void setMaxHistoryLength(int maxHistoryLength) {
        lock.writeLock().lock();
        try {
            this.maxHistoryLength = maxHistoryLength;
            
            // Remove oldest messages if exceeding new max length
            while (messages.size() > maxHistoryLength) {
                messages.remove(0);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the maximum history length
     * @return The maximum number of messages to keep
     */
    public int getMaxHistoryLength() {
        return maxHistoryLength;
    }
    
    /**
     * Get the current number of messages in history
     * @return The current message count
     */
    public int getMessageCount() {
        lock.readLock().lock();
        try {
            return messages.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}