package org.example.feedbackloop.controllers;

import org.example.feedbackloop.models.WhatsAppMessage;
import org.example.feedbackloop.models.SentimentAnalysis;
import org.example.feedbackloop.repositories.WhatsAppMessageRepository;
import org.example.feedbackloop.services.AISentimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    @Autowired
    private AISentimentService aiSentimentService;

    @Autowired
    private WhatsAppMessageRepository messageRepository;

    @PostMapping("/message")
    public ResponseEntity<WhatsAppMessage> receiveMessage(@RequestBody Map<String, String> messageData) {
        try {
            String messageId = messageData.get("messageId");
            String fromNumber = messageData.get("fromNumber");
            String toNumber = messageData.get("toNumber");
            String content = messageData.get("content");

            WhatsAppMessage message = new WhatsAppMessage(messageId, fromNumber, toNumber, content);

            // AI analyzes sentiment
            SentimentAnalysis aiAnalysis = aiSentimentService.analyzeSentiment(content);
            message.setAiSentiment(aiAnalysis);

            WhatsAppMessage savedMessage = messageRepository.save(message);

            return ResponseEntity.ok(savedMessage);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/message/{id}/override-sentiment")
    public ResponseEntity<WhatsAppMessage> overrideSentiment(
            @PathVariable String id,
            @RequestBody SentimentAnalysis humanSentiment) {

        Optional<WhatsAppMessage> messageOpt = messageRepository.findById(id);

        if (messageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WhatsAppMessage message = messageOpt.get();
        humanSentiment.setAnalyzedBy("HUMAN");
        message.setHumanSentiment(humanSentiment);

        WhatsAppMessage updatedMessage = messageRepository.save(message);

        // This change will trigger the MongoDB change stream
        return ResponseEntity.ok(updatedMessage);
    }

    @GetMapping("/message/{id}")
    public ResponseEntity<WhatsAppMessage> getMessage(@PathVariable String id) {
        Optional<WhatsAppMessage> message = messageRepository.findById(id);
        return message.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/messages")
    public ResponseEntity<Iterable<WhatsAppMessage>> getAllMessages() {
        return ResponseEntity.ok(messageRepository.findAll());
    }
}
