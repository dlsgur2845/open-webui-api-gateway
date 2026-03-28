package com.openwebui.gateway.dto;

import java.time.Instant;

public record ErrorResponse(
        int status,
        String message,
        String timestamp
) {
    public ErrorResponse(int status, String message) {
        this(status, message, Instant.now().toString());
    }
}
