package com.pulsedesk.exception;

import lombok.Getter;

@Getter
public class TicketNotFoundException extends RuntimeException {

    private final Long ticketId;

    public TicketNotFoundException(Long ticketId) {
        super("Ticket not found with id: " + ticketId);
        this.ticketId = ticketId;
    }

}