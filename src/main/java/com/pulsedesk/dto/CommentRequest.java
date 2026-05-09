package com.pulsedesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {

    @NotBlank(message = "Author name is required")
    @Size(max = 100, message = "Author name must be 100 characters or fewer")
    private String author;

    @NotBlank(message = "Comment text is required")
    @Size(min = 5, max = 2000, message = "Comment must be between 5 and 2000 characters")
    private String text;

    private String channel = "web_form";
}