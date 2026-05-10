package com.pulsedesk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsedesk.dto.HuggingFaceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HuggingFaceService {

    @Value("${huggingface.api.token}")
    private String apiToken;

    @Value("${huggingface.api.url}")
    private String apiUrl;

    @Value("${huggingface.api.model}")
    private String apiModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Sends a comment to the Hugging Face model and returns a
     * structured triage result.
     *
     * @param commentText the raw user comment to analyze
     * @return HuggingFaceResult with isTicket flag and ticket fields if applicable
     */
    public HuggingFaceResult analyze(String commentText) {
        log.debug("Sending comment to HuggingFace API: '{}'", commentText);

        String prompt = buildPrompt(commentText);
        String rawResponse = callApi(prompt);

        log.debug("Raw HuggingFace response: {}", rawResponse);

        return parseResponse(rawResponse);
    }

    // -- Private helpers --

    private String buildPrompt(String commentText) {
        return """
                You are a support triage assistant for a platform called PulseDesk.
                Analyze the user comment below and respond ONLY with a single valid JSON object.
                Do not write any explanation, markdown, or text outside the JSON.

                Comment: "%s"

                Respond using this exact JSON structure:
                {
                  "isTicket": true or false,
                  "title": "short ticket title, max 10 words, or null",
                  "category": "bug" or "feature" or "billing" or "account" or "other" or null,
                  "priority": "low" or "medium" or "high" or null,
                  "summary": "one sentence describing the issue, or null"
                }

                Rules:
                - Set isTicket to true only if the comment describes a real problem, bug, or actionable request
                - Set isTicket to false for compliments, greetings, vague feedback, or off-topic messages
                - When isTicket is false, set title, category, priority, summary all to null
                - Priority: high = app is broken or data is lost; medium = feature broken but workaround exists; low = minor issue or improvement
                """.formatted(commentText);
    }

    /**
     * Makes the HTTP POST request to the Hugging Face Inference API.
     * Returns the raw response body as a String.
     */
    private String callApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> requestBody = Map.of(
                "model", apiModel,
                "messages", List.of(message),
                "max_tokens", 300,
                "temperature", 0.1
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiUrl, request, String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("HuggingFace API returned non-success status: {}", response.getStatusCode());
                return fallbackResult();
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            JsonNode content = root.path("choices").get(0).path("message").path("content");

            if (content.isMissingNode()) {
                log.error("Unexpected HuggingFace response structure: {}", response.getBody());
                return fallbackResult();
            }

            return content.asText();

        } catch (Exception e) {
            log.error("Failed to call HuggingFace API: {}", e.getMessage());
            return fallbackResult();
        }
    }

    /**
     * Parses the raw model output text into a HuggingFaceResult.
     * Mistral sometimes adds extra text around the JSON, so we extract
     * just the JSON block before parsing.
     */
    private HuggingFaceResult parseResponse(String rawText) {
        try {
            int start = rawText.indexOf('{');
            int end = rawText.lastIndexOf('}');

            if (start == -1 || end == -1 || end <= start) {
                log.warn("Could not find JSON block in model response, using fallback.");
                return buildFallbackResult();
            }

            String jsonBlock = rawText.substring(start, end + 1);
            log.debug("Extracted JSON block: {}", jsonBlock);

            JsonNode node = objectMapper.readTree(jsonBlock);

            boolean isTicket = node.path("isTicket").asBoolean(false);

            if (!isTicket) {
                return HuggingFaceResult.builder().isTicket(false).build();
            }

            return HuggingFaceResult.builder()
                    .isTicket(true)
                    .title(getTextOrNull(node, "title"))
                    .category(getTextOrNull(node, "category"))
                    .priority(getTextOrNull(node, "priority"))
                    .summary(getTextOrNull(node, "summary"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse HuggingFace JSON response: {}", e.getMessage());
            return buildFallbackResult();
        }
    }

    /**
     * Safely reads a text field from a JsonNode.
     * Returns null (not the string "null") if the node is missing or explicitly null.
     */
    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) return null;
        String text = value.asText().trim();
        return (text.isEmpty() || text.equalsIgnoreCase("null")) ? null : text;
    }

    /**
     * Returns a JSON string representing a "not a ticket" fallback.
     * Used when the API call fails entirely so the app still works.
     */
    private String fallbackResult() {
        return "{\"isTicket\": false}";
    }

    private HuggingFaceResult buildFallbackResult() {
        return HuggingFaceResult.builder().isTicket(false).build();
    }
}