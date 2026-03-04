package org.example.feedbackloop.models;

import java.time.LocalDateTime;

public class SentimentAnalysis {
    
    private String sentiment; // POSITIVE, NEGATIVE, NEUTRAL
    private String productInterest; // HIGH, MEDIUM, LOW, NONE
    private double confidence;
    private String reasoning;
    private String analyzedBy; // AI or HUMAN
    private LocalDateTime analysisTime;
    
    public SentimentAnalysis() {}
    
    public SentimentAnalysis(String sentiment, String productInterest, double confidence, String reasoning, String analyzedBy) {
        this.sentiment = sentiment;
        this.productInterest = productInterest;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.analyzedBy = analyzedBy;
        this.analysisTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    
    public String getProductInterest() { return productInterest; }
    public void setProductInterest(String productInterest) { this.productInterest = productInterest; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    
    public String getAnalyzedBy() { return analyzedBy; }
    public void setAnalyzedBy(String analyzedBy) { this.analyzedBy = analyzedBy; }
    
    public LocalDateTime getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(LocalDateTime analysisTime) { this.analysisTime = analysisTime; }
}
