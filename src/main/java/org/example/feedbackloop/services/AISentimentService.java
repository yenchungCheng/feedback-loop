package org.example.feedbackloop.services;

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
import java.util.Optional;
import java.time.LocalDateTime;

@Service
public class AISentimentService {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;
    
    @Autowired
    private PromptContextRepository promptContextRepository;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String DEFAULT_SENTIMENT_PROMPT = 
        "Analyze the following WhatsApp message and extract:\n" +
        "1. User Sentiment (POSITIVE, NEGATIVE, NEUTRAL)\n" +
        "2. Product Interest (HIGH, MEDIUM, LOW, NONE)\n" +
        "3. Confidence level (0.0-1.0)\n" +
        "4. Brief reasoning for your analysis\n\n" +
        "Message: {message}\n\n" +
        "Respond in JSON format: {\"sentiment\": \"SENTIMENT\", \"productInterest\": \"INTEREST\", \"confidence\": 0.0, \"reasoning\": \"explanation\"}";
    
    public SentimentAnalysis analyzeSentiment(String messageContent) {
        try {
            String prompt = getOptimizedPrompt("SENTIMENT_EXTRACTION", messageContent);
            String response = callOpenAI(prompt);
            return parseSentimentResponse(response);
        } catch (Exception e) {
            // Fallback to basic analysis
            return createFallbackAnalysis(messageContent);
        }
    }
    
    private String getOptimizedPrompt(String contextType, String messageContent) {
        Optional<PromptContext> optimizedContext = promptContextRepository
            .findFirstByContextTypeOrderBySuccessRateDesc(contextType);
        
        if (optimizedContext.isPresent()) {
            PromptContext context = optimizedContext.get();
            context.incrementUsage();
            promptContextRepository.save(context);
            return context.getImprovedPrompt().replace("{message}", messageContent);
        }
        
        return DEFAULT_SENTIMENT_PROMPT.replace("{message}", messageContent);
    }
    
    private String callOpenAI(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openaiApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = Map.of(
            "model", "gpt-3.5-turbo",
            "messages", List.of(
                Map.of("role", "system", "content", "You are a sentiment analysis expert for WhatsApp messages."),
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", 150,
            "temperature", 0.3
        );
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(openaiApiUrl, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.err.println("OpenAI API call failed: " + e.getMessage());
        }
        
        throw new RuntimeException("Failed to call OpenAI API");
    }
    
    private SentimentAnalysis parseSentimentResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).get("message").get("content").asText();
                JsonNode analysis = objectMapper.readTree(content);
                
                return new SentimentAnalysis(
                    analysis.get("sentiment").asText(),
                    analysis.get("productInterest").asText(),
                    analysis.get("confidence").asDouble(),
                    analysis.get("reasoning").asText(),
                    "AI"
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to parse OpenAI response: " + e.getMessage());
        }
        
        throw new RuntimeException("Failed to parse sentiment response");
    }
    
    private SentimentAnalysis createFallbackAnalysis(String messageContent) {
        String lowerContent = messageContent.toLowerCase();
        String sentiment = "NEUTRAL";
        String productInterest = "LOW";
        double confidence = 0.5;
        String reasoning = "Fallback analysis due to API failure";
        
        if (lowerContent.contains("love") || lowerContent.contains("great") || lowerContent.contains("awesome")) {
            sentiment = "POSITIVE";
            productInterest = "HIGH";
            confidence = 0.7;
            reasoning = "Positive keywords detected";
        } else if (lowerContent.contains("hate") || lowerContent.contains("terrible") || lowerContent.contains("bad")) {
            sentiment = "NEGATIVE";
            productInterest = "LOW";
            confidence = 0.7;
            reasoning = "Negative keywords detected";
        }
        
        return new SentimentAnalysis(sentiment, productInterest, confidence, reasoning, "AI_FALLBACK");
    }
}
