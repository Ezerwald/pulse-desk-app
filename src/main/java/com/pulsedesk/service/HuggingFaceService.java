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
            You are a strict, objective support triage assistant for a platform called PulseDesk.
            Your job is to classify user comments and extract structured ticket data.
            Respond ONLY with a single valid JSON object — no explanation, no markdown, no text outside the JSON.
            Always respond in English regardless of the language the comment is written in.

            <comment>
            %s
            </comment>

            Respond using this exact JSON structure:
            {
              "isTicket": true or false,
              "title": "short ticket title, max 10 words, or null",
              "category": "bug" or "feature" or "billing" or "account" or "other" or null,
              "priority": "low" or "medium" or "high" or null,
              "summary": "one sentence describing the issue, or null"
            }

            === TICKET CREATION RULES ===
            Set isTicket to TRUE only when ALL of these apply:
            - The comment describes a specific, reproducible problem or a clearly actionable feature request
            - The issue would affect or benefit ALL users, not just the person writing
            - There is enough detail to act on (not just "it's broken" with nothing else)

            Set isTicket to FALSE for:
            - Compliments, greetings, or thank-you messages ("Love the app!", "Great work!")
            - Vague negativity with no specifics ("This app is terrible", "Nothing works")
            - Personal aesthetic preferences ("make it pink", "I prefer a different font")
            - User questions ("Why does the app need location access?")
            - Issues the user says are already resolved ("it crashed yesterday but works now")
            - Sarcasm without a real described problem ("Great job breaking it again 🙄")
            - Off-topic or contact requests ("Can I get a refund? Email me at...")
            - Casual wishes or vague suggestions ("Would be cool if someday maybe...")

            === PRIORITY RULES ===
            Priority is determined by OBJECTIVE IMPACT only — never by the user's tone, emotion, or their own urgency words:
            - high   = core functionality is broken, data is lost, or the app is unusable
            - medium = a feature is broken but a workaround exists, or a widely-needed improvement
            - low    = minor cosmetic issue, niche improvement, or non-critical inconvenience

            A user writing "URGENT", "highest priority", or using exclamation marks does NOT raise the priority.
            A user demanding something personal does NOT make it high priority.
            Use "other" only when none of bug / feature / billing / account clearly fits.

            === WHEN isTicket IS FALSE ===
            Set title, category, priority, and summary all to null — no exceptions.

            === EXAMPLES ===

            Comment: "The login button crashes the app every single time I tap it on Android. This started after the last update."
            Output: {"isTicket": true, "title": "Android login button crash after update", "category": "bug", "priority": "high", "summary": "Login button consistently crashes the app on Android since the last update."}

            Comment: "Make the menu button pink please! I love pink and it's my highest priority request!!!"
            Output: {"isTicket": false, "title": null, "category": null, "priority": null, "summary": null}

            Comment: "It would be great if we could export our data to CSV someday."
            Output: {"isTicket": true, "title": "Add CSV data export feature", "category": "feature", "priority": "low", "summary": "User requests the ability to export their data as a CSV file."}

            Comment: "I was charged twice for my subscription this month."
            Output: {"isTicket": true, "title": "Duplicate subscription charge", "category": "billing", "priority": "high", "summary": "User reports being charged twice for their subscription in the same month."}

            Comment: "This app is absolutely terrible, I hate it!!!"
            Output: {"isTicket": false, "title": null, "category": null, "priority": null, "summary": null}
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