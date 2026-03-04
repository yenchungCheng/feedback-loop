package org.example.feedbackloop.services;

import org.example.feedbackloop.models.WhatsAppMessage;
import org.example.feedbackloop.models.SentimentAnalysis;
import org.example.feedbackloop.models.PromptContext;
import org.example.feedbackloop.repositories.PromptContextRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class CriticAgentService {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;
    
    @Autowired
    private PromptContextRepository promptContextRepository;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String CRITIC_PROMPT_TEMPLATE = 
        "You are a critic agent analyzing AI sentiment analysis errors. " +
        "Original message: \"{message}\"\n" +
        "AI analysis: Sentiment={aiSentiment}, ProductInterest={aiInterest}, Reasoning={aiReasoning}\n" +
        "Human corrected analysis: Sentiment={humanSentiment}, ProductInterest={humanInterest}\n\n" +
        "Analyze why the AI was wrong and generate an improved prompt for future analysis. " +
        "Respond in JSON format: {\"errorAnalysis\": \"explanation\", \"improvedPrompt\": \"new prompt template\", \"keyInsights\": [\"insight1\", \"insight2\"]}";
    
    public void analyzeAndImprove(WhatsAppMessage message) {
        if (message.getAiSentiment() == null || message.getHumanSentiment() == null) {
            return;
        }
        
        SentimentAnalysis aiAnalysis = message.getAiSentiment();
        SentimentAnalysis humanAnalysis = message.getHumanSentiment();
        
        // Only analyze if there's a significant difference
        if (isSignificantDifference(aiAnalysis, humanAnalysis)) {
            try {
                String criticResponse = callCriticAgent(message, aiAnalysis, humanAnalysis);
                processCriticResponse(criticResponse, message.getContent());
            } catch (Exception e) {
                System.err.println("Critic agent analysis failed: " + e.getMessage());
            }
        }
    }
    
    private boolean isSignificantDifference(SentimentAnalysis ai, SentimentAnalysis human) {
        return !ai.getSentiment().equals(human.getSentiment()) || 
               !ai.getProductInterest().equals(human.getProductInterest());
    }
    
    private String callCriticAgent(WhatsAppMessage message, SentimentAnalysis ai, SentimentAnalysis human) {
        String prompt = CRITIC_PROMPT_TEMPLATE
            .replace("{message}", message.getContent())
            .replace("{aiSentiment}", ai.getSentiment())
            .replace("{aiInterest}", ai.getProductInterest())
            .replace("{aiReasoning}", ai.getReasoning())
            .replace("{humanSentiment}", human.getSentiment())
            .replace("{humanInterest}", human.getProductInterest());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openaiApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = Map.of(
            "model", "gpt-3.5-turbo",
            "messages", List.of(
                Map.of("role", "system", "content", "You are an expert at analyzing AI sentiment analysis errors and improving prompts."),
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", 300,
            "temperature", 0.3
        );
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        }
        
        throw new RuntimeException("Failed to call critic agent");
    }
    
    private void processCriticResponse(String response, String originalMessage) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).get("message").get("content").asText();
                JsonNode analysis = objectMapper.readTree(content);
                
                String errorAnalysis = analysis.get("errorAnalysis").asText();
                String improvedPrompt = analysis.get("improvedPrompt").asText();
                
                // Update sentiment extraction prompt
                updatePromptContext("SENTIMENT_EXTRACTION", improvedPrompt, errorAnalysis);
                
                // Also update product interest prompt if relevant
                if (originalMessage.toLowerCase().contains("product") || 
                    originalMessage.toLowerCase().contains("buy") ||
                    originalMessage.toLowerCase().contains("price")) {
                    updatePromptContext("PRODUCT_INTEREST", improvedPrompt, errorAnalysis);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to process critic response: " + e.getMessage());
        }
    }
    
    private void updatePromptContext(String contextType, String improvedPrompt, String feedbackReasoning) {
        Optional<PromptContext> existingContext = promptContextRepository
            .findFirstByContextTypeOrderBySuccessRateDesc(contextType);
        
        PromptContext context;
        if (existingContext.isPresent()) {
            context = existingContext.get();
            context.setImprovedPrompt(improvedPrompt);
            context.setFeedbackReasoning(feedbackReasoning);
            context.setUpdatedAt(LocalDateTime.now());
        } else {
            context = new PromptContext(contextType, improvedPrompt, 
                "Default prompt", feedbackReasoning);
        }
        
        promptContextRepository.save(context);
    }
}
