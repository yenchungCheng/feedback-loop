package org.example.feedbackloop.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "whatsapp_messages")
public class WhatsAppMessage {
    
    @Id
    private String id;
    private String messageId;
    private String fromNumber;
    private String toNumber;
    private String content;
    private LocalDateTime timestamp;
    private SentimentAnalysis aiSentiment;
    private SentimentAnalysis humanSentiment;
    private boolean processed;
    
    public WhatsAppMessage() {}
    
    public WhatsAppMessage(String messageId, String fromNumber, String toNumber, String content) {
        this.messageId = messageId;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.processed = false;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    
    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public SentimentAnalysis getAiSentiment() { return aiSentiment; }
    public void setAiSentiment(SentimentAnalysis aiSentiment) { this.aiSentiment = aiSentiment; }
    
    public SentimentAnalysis getHumanSentiment() { return humanSentiment; }
    public void setHumanSentiment(SentimentAnalysis humanSentiment) { this.humanSentiment = humanSentiment; }
    
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
}
