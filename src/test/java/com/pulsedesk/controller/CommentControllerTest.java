package com.pulsedesk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsedesk.dto.HuggingFaceResult;
import com.pulsedesk.service.HuggingFaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @SpringBootTest: boots the full application context with real beans and H2 DB
// @AutoConfigureMockMvc: sets up MockMvc to simulate HTTP calls without a real server
// @DirtiesContext: resets the Spring context between tests so data doesn't bleed across
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;        // Simulates HTTP requests — no real server needed

    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean replaces the real HuggingFaceService bean in the Spring context
    // with a Mockito mock — so no real API calls are made during tests
    @MockBean
    private HuggingFaceService huggingFaceService;

    @Test
    @DisplayName("POST /comments should return 201 and create a ticket when AI flags the comment")
    void postComment_shouldReturn201WithTicket_whenAiCreatesTicket() throws Exception {
        // ── Arrange: tell the mock what to return ────────────────────────────
        HuggingFaceResult ticketResult = HuggingFaceResult.builder()
                .isTicket(true)
                .title("Login button crash")
                .category("bug")
                .priority("high")
                .summary("Login button crashes app on Android.")
                .build();

        when(huggingFaceService.analyze(anyString())).thenReturn(ticketResult);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "author",  "Anna",
                "text",    "The login button crashes the app every time on Android.",
                "channel", "app_review"
        ));

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(post("/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())                          // HTTP 201
                .andExpect(jsonPath("$.author").value("Anna"))
                .andExpect(jsonPath("$.hasTicket").value(true))
                .andExpect(jsonPath("$.ticket").exists())
                .andExpect(jsonPath("$.ticket.category").value("BUG"))
                .andExpect(jsonPath("$.ticket.priority").value("HIGH"));
    }

    @Test
    @DisplayName("POST /comments should return 201 with null ticket when AI does not flag comment")
    void postComment_shouldReturn201WithNoTicket_whenAiSkipsTicket() throws Exception {
        when(huggingFaceService.analyze(anyString()))
                .thenReturn(HuggingFaceResult.builder().isTicket(false).build());

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "author",  "Bob",
                "text",    "Love the new dashboard design, really clean!",
                "channel", "web_form"
        ));

        mockMvc.perform(post("/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasTicket").value(false))
                .andExpect(jsonPath("$.ticket").doesNotExist());
    }

    @Test
    @DisplayName("POST /comments should return 400 when request body is invalid")
    void postComment_shouldReturn400_whenRequestIsInvalid() throws Exception {
        String invalidBody = objectMapper.writeValueAsString(Map.of(
                "author", "",     // blank — violates @NotBlank
                "text",   "Hi"    // too short — violates @Size(min=5)
        ));

        mockMvc.perform(post("/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.author").exists())
                .andExpect(jsonPath("$.errors.text").exists());
    }

    @Test
    @DisplayName("GET /comments should return all submitted comments")
    void getComments_shouldReturnAllComments() throws Exception {
        // First submit a comment
        when(huggingFaceService.analyze(anyString()))
                .thenReturn(HuggingFaceResult.builder().isTicket(false).build());

        mockMvc.perform(post("/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "author", "Eve", "text", "Nice update overall.", "channel", "chat"
                ))));

        // Then retrieve all
        mockMvc.perform(get("/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].author").value("Eve"));
    }

    @Test
    @DisplayName("GET /tickets/{id} should return 404 for non-existent ticket")
    void getTicketById_shouldReturn404_whenTicketDoesNotExist() throws Exception {
        mockMvc.perform(get("/tickets/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found with id: 9999"));
    }
}