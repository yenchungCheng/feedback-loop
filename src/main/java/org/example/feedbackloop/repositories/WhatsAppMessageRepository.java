package org.example.feedbackloop.repositories;

import org.example.feedbackloop.models.WhatsAppMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppMessageRepository extends MongoRepository<WhatsAppMessage, String> {
    Optional<WhatsAppMessage> findByMessageId(String messageId);
}
