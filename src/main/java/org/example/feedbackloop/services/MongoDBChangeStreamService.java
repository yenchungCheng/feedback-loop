package org.example.feedbackloop.services;

import jakarta.annotation.PostConstruct;
import org.example.feedbackloop.models.WhatsAppMessage;
import org.example.feedbackloop.repositories.WhatsAppMessageRepository;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;


import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MongoDBChangeStreamService {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private WhatsAppMessageRepository messageRepository;
    
    @Autowired
    private CriticAgentService criticAgentService;
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    @PostConstruct
    public void startChangeStreamListener() {
        executorService.submit(() -> {
            try {
                listenForChanges();
            } catch (Exception e) {
                System.err.println("Change stream listener error: " + e.getMessage());
                // Restart the listener after a delay
                try {
                    Thread.sleep(5000);
                    startChangeStreamListener();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
    
    private void listenForChanges() {
        // Create pipeline to filter for updates on humanSentiment field
        BsonDocument pipeline = BsonDocument.parse("""
            {
                $match: {
                    $and: [
                        { operationType: 'update' },
                        { 'updateDescription.updatedFields.humanSentiment': { $exists: true } }
                    ]
                }
            }
            """);
        
        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = 
                mongoTemplate.getCollection("whatsapp_messages")
                    .watch(Collections.singletonList(pipeline))
                    .cursor()) {
            
            while (cursor.hasNext()) {
                ChangeStreamDocument<Document> change = cursor.next();
                processChange(change);
            }
        }
    }
    
    private void processChange(ChangeStreamDocument<Document> change) {
        try {
            String messageId = change.getDocumentKey().getString("_id").getValue();
            
            // Fetch the full updated document
            WhatsAppMessage updatedMessage = messageRepository.findById(messageId).orElse(null);
            
            if (updatedMessage != null && 
                updatedMessage.getAiSentiment() != null && 
                updatedMessage.getHumanSentiment() != null) {
                
                System.out.println("Human sentiment override detected for message: " + messageId);
                System.out.println("AI: " + updatedMessage.getAiSentiment().getSentiment() + 
                                 " -> Human: " + updatedMessage.getHumanSentiment().getSentiment());
                
                // Trigger the critic agent
                criticAgentService.analyzeAndImprove(updatedMessage);
            }
        } catch (Exception e) {
            System.err.println("Error processing change: " + e.getMessage());
        }
    }
}
