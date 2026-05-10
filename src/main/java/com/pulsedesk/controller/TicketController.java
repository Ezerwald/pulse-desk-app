package com.pulsedesk.controller;

import com.pulsedesk.dto.TicketResponse;
import com.pulsedesk.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "View AI-generated support tickets")
public class TicketController {

    private final CommentService commentService;

    /**
     * GET /tickets
     * Returns all tickets that were generated from comments.
     */
    @GetMapping
    @Operation(
            summary = "Get all tickets",
            description = "Returns all support tickets created by the AI triage process."
    )
    @ApiResponse(responseCode = "200", description = "List of tickets returned successfully")
    public ResponseEntity<List<TicketResponse>> getAllTickets() {
        log.info("GET /tickets");
        return ResponseEntity.ok(commentService.getAllTickets());
    }

    /**
     * GET /tickets/{ticketId}
     * Returns a single ticket by its ID.
     * Throws TicketNotFoundException if the ID doesn't exist.
     */
    @GetMapping("/{ticketId}")
    @Operation(
            summary = "Get a ticket by ID",
            description = "Returns the full details of a single support ticket. " +
                    "Returns 404 if the ticket does not exist."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket found and returned"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public ResponseEntity<TicketResponse> getTicketById(
            @Parameter(description = "The numeric ID of the ticket")
            @PathVariable Long ticketId) {

        log.info("GET /tickets/{}", ticketId);
        return ResponseEntity.ok(commentService.getTicketById(ticketId));
    }
}