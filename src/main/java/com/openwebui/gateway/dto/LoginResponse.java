package com.openwebui.gateway.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresAt,    // epoch seconds
        String id,
        String email,
        String name,
        String role,
        String profileImageUrl
) {
    public LoginResponse(String token, long expiresAt,
                         String id, String email, String name,
                         String role, String profileImageUrl) {
        this(token, "Bearer", expiresAt, id, email, name, role, profileImageUrl);
    }
}
