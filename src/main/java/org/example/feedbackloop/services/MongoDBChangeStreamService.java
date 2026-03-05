package org.example.feedbackloop.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.feedbackloop.models.WhatsAppMessage;
import org.example.feedbackloop.repositories.WhatsAppMessageRepository;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;


import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MongoDBChangeStreamService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private WhatsAppMessageRepository messageRepository;

    @Autowired
    private CriticAgentService criticAgentService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor;

    @PostConstruct
    public void startChangeStreamListener() {
        if (running.compareAndSet(false, true)) {
            System.out.println("🔄 正在啟動 Change Stream 監聽器...");
            executorService.submit(this::listenForChanges);
        }
    }

    @PreDestroy
    public void stopChangeStreamListener() {
        running.set(false);
        closeCursor();
        executorService.shutdown();
        System.out.println("✓ Change Stream 監聽器已停止");
    }

    private void listenForChanges() {
        while (running.get()) {
            try {
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

                cursor = mongoTemplate.getCollection("whatsapp_messages")
                        .watch(Collections.singletonList(pipeline))
                        .cursor();

                System.out.println("✓ Change Stream 連線成功，開始監聽...");

                while (running.get() && cursor != null) {
                    try {
                        if (cursor.hasNext()) {
                            ChangeStreamDocument<Document> change = cursor.next();
                            processChange(change);
                        } else {
                            // 沒有新事件，短暫休息避免 CPU 空轉
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        System.err.println("處理 Change Stream 事件時發生錯誤: " + e.getMessage());
                        break;
                    }
                }

            } catch (Exception e) {
                System.err.println("Change Stream 連線錯誤: " + e.getMessage());
            } finally {
                closeCursor();
            }

            // 如果還在運行狀態，等待後重新連線
            if (running.get()) {
                try {
                    System.out.println("等待 5 秒後重新連線...");
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running.set(false);
                }
            }
        }

        System.out.println("Change Stream 監聽器已結束");
    }

    private void closeCursor() {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception e) {
                System.err.println("關閉 cursor 時發生錯誤: " + e.getMessage());
            }
            cursor = null;
        }
    }

    private void processChange(ChangeStreamDocument<Document> change) {
        try {
            // 獲取 ObjectId 並轉換為 String
            String messageId = change.getDocumentKey().getObjectId("_id").getValue().toHexString();

            System.out.println("🔔 偵測到訊息更新: " + messageId);

            // Fetch the full updated document
            WhatsAppMessage updatedMessage = messageRepository.findById(messageId).orElse(null);

            if (updatedMessage != null &&
                    updatedMessage.getAiSentiment() != null &&
                    updatedMessage.getHumanSentiment() != null) {

                System.out.println("✅ Human sentiment override detected for message: " + messageId);
                System.out.println("   AI: " + updatedMessage.getAiSentiment().getSentiment() +
                        " -> Human: " + updatedMessage.getHumanSentiment().getSentiment());

                // Trigger the critic agent
                criticAgentService.analyzeAndImprove(updatedMessage);
            } else {
                System.out.println("⚠️ 訊息不完整，跳過處理");
            }
        } catch (Exception e) {
            System.err.println("❌ Error processing change: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
