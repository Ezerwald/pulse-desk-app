package com.pulsedesk.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pulseDeskOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PulseDesk API")
                        .description("Comment-to-Ticket Triage System")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PulseDesk")
                                .email("contact@pulsedesk.com")));
    }
}