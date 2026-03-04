package org.example.feedbackloop.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "prompt_contexts")
public class PromptContext {
    
    @Id
    private String id;
    private String contextType; // SENTIMENT_EXTRACTION, PRODUCT_INTEREST
    private String improvedPrompt;
    private String originalPrompt;
    private String feedbackReasoning;
    private int usageCount;
    private double successRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public PromptContext() {}
    
    public PromptContext(String contextType, String improvedPrompt, String originalPrompt, String feedbackReasoning) {
        this.contextType = contextType;
        this.improvedPrompt = improvedPrompt;
        this.originalPrompt = originalPrompt;
        this.feedbackReasoning = feedbackReasoning;
        this.usageCount = 0;
        this.successRate = 0.0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getContextType() { return contextType; }
    public void setContextType(String contextType) { this.contextType = contextType; }
    
    public String getImprovedPrompt() { return improvedPrompt; }
    public void setImprovedPrompt(String improvedPrompt) { this.improvedPrompt = improvedPrompt; }
    
    public String getOriginalPrompt() { return originalPrompt; }
    public void setOriginalPrompt(String originalPrompt) { this.originalPrompt = originalPrompt; }
    
    public String getFeedbackReasoning() { return feedbackReasoning; }
    public void setFeedbackReasoning(String feedbackReasoning) { this.feedbackReasoning = feedbackReasoning; }
    
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public void incrementUsage() {
        this.usageCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
