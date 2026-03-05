package org.example.feedbackloop.services;

import org.example.feedbackloop.models.WhatsAppMessage;
import org.example.feedbackloop.models.SentimentAnalysis;
import org.example.feedbackloop.models.PromptContext;
import org.example.feedbackloop.repositories.PromptContextRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CriticAgentService {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private PromptContextRepository promptContextRepository;

    private static final String CRITIC_PROMPT_TEMPLATE =
            """
                    You are a critic agent analyzing AI sentiment analysis errors. \
                    Original message: "{message}"
                    AI analysis: Sentiment={aiSentiment}, ProductInterest={aiInterest}, Reasoning={aiReasoning}
                    Human corrected analysis: Sentiment={humanSentiment}, ProductInterest={humanInterest}
                    
                    Analyze why the AI was wrong and generate an improved prompt for future analysis. \
                    Respond in JSON format: {"errorAnalysis": "explanation", "improvedPrompt": "new prompt template", "keyInsights": ["insight1", "insight2"]}""";

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
                System.err.println("Gemini critic agent analysis failed: " + e.getMessage());
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

        try {
            List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                    UserMessage.from("You are an expert at analyzing AI sentiment analysis errors and improving prompts. Always respond in valid JSON format."),
                    UserMessage.from(prompt)
            );

            AiMessage response = chatLanguageModel.generate(messages).content();
            return response.text();
        } catch (Exception e) {
            System.err.println("Gemini critic agent call failed: " + e.getMessage());
            throw new RuntimeException("Failed to call Gemini critic agent", e);
        }
    }

    private void processCriticResponse(String response, String originalMessage) {
        try {
            // Extract JSON from response
            Pattern jsonPattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);

            if (matcher.find()) {
                String jsonStr = matcher.group();

                String errorAnalysis = extractJsonValue(jsonStr, "errorAnalysis");
                String improvedPrompt = extractJsonValue(jsonStr, "improvedPrompt");

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
            System.err.println("Failed to process Gemini critic response: " + e.getMessage());
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
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
