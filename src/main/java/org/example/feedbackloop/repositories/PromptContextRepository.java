package org.example.feedbackloop.repositories;

import org.example.feedbackloop.models.PromptContext;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptContextRepository extends MongoRepository<PromptContext, String> {
    Optional<PromptContext> findFirstByContextTypeOrderBySuccessRateDesc(String contextType);
}
