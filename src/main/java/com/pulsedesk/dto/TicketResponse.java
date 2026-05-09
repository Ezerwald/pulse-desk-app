package com.pulsedesk.dto;

import com.pulsedesk.model.Ticket;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponse {

    private Long id;
    private Long commentId;
    private String title;
    private String category;
    private String priority;
    private String summary;
    private LocalDateTime createdAt;

    public static TicketResponse from(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .commentId(ticket.getComment().getId())
                .title(ticket.getTitle())
                .category(ticket.getCategory().name())
                .priority(ticket.getPriority().name())
                .summary(ticket.getSummary())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}