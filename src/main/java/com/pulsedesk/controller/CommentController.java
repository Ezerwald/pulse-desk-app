package com.pulsedesk.controller;

import com.pulsedesk.dto.CommentRequest;
import com.pulsedesk.dto.CommentResponse;
import com.pulsedesk.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Submit and view user comments")
public class CommentController {

    private final CommentService commentService;

    /**
     * POST /comments
     * Accepts a new comment, runs it through AI triage, and returns the result.
     */
    @PostMapping
    @Operation(
            summary = "Submit a new comment",
            description = "Saves the comment and automatically runs AI triage. " +
                    "If the comment is flagged as an issue, a ticket is created and returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<CommentResponse> submitComment(
            @Valid @RequestBody CommentRequest request) {

        log.info("POST /comments — author='{}'", request.getAuthor());
        CommentResponse response = commentService.processComment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /comments
     * Returns all submitted comments, each with its linked ticket if one exists.
     */
    @GetMapping
    @Operation(
            summary = "Get all comments",
            description = "Returns all submitted comments. " +
                    "Each comment includes a nested ticket object if one was created."
    )
    @ApiResponse(responseCode = "200", description = "List of comments returned successfully")
    public ResponseEntity<List<CommentResponse>> getAllComments() {
        log.info("GET /comments");
        return ResponseEntity.ok(commentService.getAllComments());
    }
}