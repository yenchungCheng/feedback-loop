package org.example.feedbackloop.services;

import org.example.feedbackloop.models.SentimentAnalysis;
import org.example.feedbackloop.models.PromptContext;
import org.example.feedbackloop.repositories.PromptContextRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AISentimentService {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private PromptContextRepository promptContextRepository;

    private static final String DEFAULT_SENTIMENT_PROMPT =
            """
                    Analyze the following WhatsApp message and extract:
                    1. User Sentiment (POSITIVE, NEGATIVE, NEUTRAL)
                    2. Product Interest (HIGH, MEDIUM, LOW, NONE)
                    3. Confidence level (0.0-1.0)
                    4. Brief reasoning for your analysis
                    
                    Message: {message}
                    
                    Respond in JSON format: {"sentiment": "SENTIMENT", "productInterest": "INTEREST", "confidence": 0.0, "reasoning": "explanation"}""";

    public SentimentAnalysis analyzeSentiment(String messageContent) {
        try {
            String prompt = getOptimizedPrompt(messageContent);
            String response = callAi(prompt);
            return parseSentimentResponse(response);
        } catch (Exception e) {
            System.err.println("Gemini AI analysis failed: " + e.getMessage());
            return createFallbackAnalysis(messageContent);
        }
    }

    private String getOptimizedPrompt(String messageContent) {
        Optional<PromptContext> optimizedContext = promptContextRepository
                .findFirstByContextTypeOrderBySuccessRateDesc("SENTIMENT_EXTRACTION");

        if (optimizedContext.isPresent()) {
            PromptContext context = optimizedContext.get();
            context.incrementUsage();
            promptContextRepository.save(context);
            return context.getImprovedPrompt().replace("{message}", messageContent);
        }

        return DEFAULT_SENTIMENT_PROMPT.replace("{message}", messageContent);
    }

    private String callAi(String prompt) {
        try {
            List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                    UserMessage.from("You are a sentiment analysis expert for WhatsApp messages. Always respond in valid JSON format."),
                    UserMessage.from(prompt)
            );

            AiMessage response = chatLanguageModel.generate(messages).content();
            return response.text();
        } catch (Exception e) {
            System.err.println("Ai call failed: " + e.getMessage());
            throw new RuntimeException("Failed to call Ai", e);
        }
    }

    private SentimentAnalysis parseSentimentResponse(String response) {
        try {
            // Extract JSON from response (in case there's extra text)
            Pattern jsonPattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);

            if (matcher.find()) {
                String jsonStr = matcher.group();

                // Parse the JSON manually to avoid Jackson dependency issues
                String sentiment = extractJsonValue(jsonStr, "sentiment");
                String productInterest = extractJsonValue(jsonStr, "productInterest");
                double confidence = extractJsonDouble(jsonStr, "confidence");
                String reasoning = extractJsonValue(jsonStr, "reasoning");

                return new SentimentAnalysis(sentiment, productInterest, confidence, reasoning, "AI");
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response: " + e.getMessage());
        }

        throw new RuntimeException("Failed to parse sentiment response");
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private double extractJsonDouble(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0.5;
            }
        }
        return 0.5;
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
