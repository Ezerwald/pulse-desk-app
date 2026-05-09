package com.pulsedesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceResult {

    private boolean isTicket;
    private String title;
    private String category;
    private String priority;
    private String summary;
}