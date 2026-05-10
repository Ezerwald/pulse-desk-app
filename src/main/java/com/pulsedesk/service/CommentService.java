package com.pulsedesk.service;

import com.pulsedesk.dto.CommentRequest;
import com.pulsedesk.dto.CommentResponse;
import com.pulsedesk.dto.HuggingFaceResult;
import com.pulsedesk.dto.TicketResponse;
import com.pulsedesk.exception.TicketNotFoundException;
import com.pulsedesk.model.Comment;
import com.pulsedesk.model.Ticket;
import com.pulsedesk.repository.CommentRepository;
import com.pulsedesk.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository  ticketRepository;
    private final HuggingFaceService huggingFaceService;

    @Transactional
    public CommentResponse processComment(CommentRequest request) {
        log.info("Processing new comment from author='{}', channel='{}'",
                request.getAuthor(), request.getChannel());

        Comment comment = Comment.builder()
                .author(request.getAuthor())
                .text(request.getText())
                .channel(request.getChannel())
                .build();

        comment = commentRepository.save(comment);
        log.debug("Saved comment with id={}", comment.getId());

        HuggingFaceResult aiResult = huggingFaceService.analyze(comment.getText());
        log.info("AI triage result for comment id={}: isTicket={}, category={}, priority={}",
                comment.getId(), aiResult.isTicket(), aiResult.getCategory(), aiResult.getPriority());

        TicketResponse ticketResponse = null;

        if (aiResult.isTicket()) {
            Ticket ticket = createTicket(comment, aiResult);
            ticket = ticketRepository.save(ticket);

            comment.setHasTicket(true);
            commentRepository.save(comment);

            ticketResponse = TicketResponse.from(ticket);
            log.info("Created ticket id={} for comment id={}", ticket.getId(), comment.getId());
        }

        return CommentResponse.from(comment, ticketResponse);
    }

    /**
     * Returns all comments with their associated tickets.
     * Uses read-only transaction for performance.
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getAllComments() {
        return commentRepository.findAll().stream()
                .map(comment -> {
                    TicketResponse ticketResponse = ticketRepository
                            .findByCommentId(comment.getId())
                            .map(TicketResponse::from)
                            .orElse(null);
                    return CommentResponse.from(comment, ticketResponse);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns all tickets.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAll().stream()
                .map(TicketResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single ticket by ID.
     * Throws TicketNotFoundException if not found.
     */
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
        return TicketResponse.from(ticket);
    }

    // -- Private helpers --

    /**
     * Converts the raw AI result strings into a fully built Ticket entity.
     * Uses safe enum parsing with fallbacks so a bad AI response never crashes the app.
     */
    private Ticket createTicket(Comment comment, HuggingFaceResult result) {
        return Ticket.builder()
                .comment(comment)
                .title(result.getTitle() != null ? result.getTitle() : "Untitled Issue")
                .category(parseCategory(result.getCategory()))
                .priority(parsePriority(result.getPriority()))
                .summary(result.getSummary() != null ? result.getSummary() : "No summary provided.")
                .build();
    }

    /**
     * Parses the AI's category string to enum safely.
     * If AI returns something unexpected, defaults to OTHER.
     */
    private Ticket.Category parseCategory(String raw) {
        if (raw == null) return Ticket.Category.OTHER;
        try {
            return Ticket.Category.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown category from AI: '{}', defaulting to OTHER", raw);
            return Ticket.Category.OTHER;
        }
    }

    /**
     * Parses the AI's priority string to enum safely.
     * If AI returns something unexpected, defaults to MEDIUM.
     */
    private Ticket.Priority parsePriority(String raw) {
        if (raw == null) return Ticket.Priority.MEDIUM;
        try {
            return Ticket.Priority.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown priority from AI: '{}', defaulting to MEDIUM", raw);
            return Ticket.Priority.MEDIUM;
        }
    }
}