package com.bsolz.lms.shared.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Browser origins allowed to call the API ({@code lms.cors}), e.g. {@code https://app.example.com}.
 * Patterns are allowed ({@code https://*.example.com}). Tokens travel in the {@code Authorization}
 * header, never in cookies, so credentials are not enabled.
 */
@Validated
@ConfigurationProperties("lms.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
