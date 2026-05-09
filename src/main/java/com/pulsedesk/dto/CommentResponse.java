package com.pulsedesk.dto;

import com.pulsedesk.model.Comment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private String author;
    private String text;
    private String channel;
    private LocalDateTime createdAt;
    private boolean hasTicket;

    private TicketResponse ticket;

    public static CommentResponse from(Comment comment, TicketResponse ticket) {
        return CommentResponse.builder()
                .id(comment.getId())
                .author(comment.getAuthor())
                .text(comment.getText())
                .channel(comment.getChannel())
                .createdAt(comment.getCreatedAt())
                .hasTicket(comment.isHasTicket())
                .ticket(ticket)
                .build();
    }
}