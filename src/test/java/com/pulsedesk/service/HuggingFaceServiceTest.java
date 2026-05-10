package com.pulsedesk.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsedesk.dto.HuggingFaceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuggingFaceServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private HuggingFaceService huggingFaceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(huggingFaceService, "apiToken", "test-token");
        ReflectionTestUtils.setField(huggingFaceService, "apiUrl", "https://fake-api.com");
        ReflectionTestUtils.setField(huggingFaceService, "apiModel", "meta-llama/Llama-3.1-8B-Instruct:novita");
        ReflectionTestUtils.setField(huggingFaceService, "objectMapper",
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    @Test
    @DisplayName("Should return isTicket=true with full fields when AI returns valid ticket JSON")
    void analyze_shouldReturnTicket_whenAiRespondsWithTicketJson() {

        // -- Arrange --
        String fakeApiResponse = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "{\\"isTicket\\": true, \\"title\\": \\"Login crash on Android\\", \\"category\\": \\"bug\\", \\"priority\\": \\"high\\", \\"summary\\": \\"Login button crashes app on Android.\\"}"
                    }
                  }]
                }
                """;

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(fakeApiResponse));

        // -- Act --
        HuggingFaceResult result = huggingFaceService.analyze("Login button crashes every time");

        // -- Assert --
        assertThat(result.isTicket()).isTrue();
        assertThat(result.getTitle()).isEqualTo("Login crash on Android");
        assertThat(result.getCategory()).isEqualTo("bug");
        assertThat(result.getPriority()).isEqualTo("high");
        assertThat(result.getSummary()).isNotBlank();
    }

    @Test
    @DisplayName("Should return isTicket=false when AI classifies comment as compliment")
    void analyze_shouldReturnNoTicket_whenAiRespondsWithFalse() {
        String fakeApiResponse = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "{\\"isTicket\\": false, \\"title\\": null, \\"category\\": null, \\"priority\\": null, \\"summary\\": null}"
                    }
                  }]
                }
                """;

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(fakeApiResponse));

        HuggingFaceResult result = huggingFaceService.analyze("Great app, love it!");

        assertThat(result.isTicket()).isFalse();
        assertThat(result.getTitle()).isNull();
    }

    @Test
    @DisplayName("Should return safe fallback when API call throws an exception")
    void analyze_shouldReturnFallback_whenApiThrowsException() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        HuggingFaceResult result = huggingFaceService.analyze("Some comment");

        assertThat(result.isTicket()).isFalse();
    }

    @Test
    @DisplayName("Should handle garbled AI response gracefully without throwing")
    void analyze_shouldHandleMalformedJson_gracefully() {
        String fakeApiResponse = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "Sorry, I cannot help with that."
                    }
                  }]
                }
                """;

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(fakeApiResponse));

        HuggingFaceResult result = huggingFaceService.analyze("Some comment");

        assertThat(result.isTicket()).isFalse();
    }
}