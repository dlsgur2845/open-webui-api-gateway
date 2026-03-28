package com.openwebui.gateway.controller;

import com.openwebui.gateway.dto.LoginRequest;
import com.openwebui.gateway.dto.LoginResponse;
import com.openwebui.gateway.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 컨트롤러.
 *
 * POST /api/v1/gateway/auth/login
 *   - email, password를 받아 open-webui DB에서 검증
 *   - 검증 성공 시 만료 기한이 있는 Gateway JWT 반환
 */
@RestController
@RequestMapping("/api/v1/gateway/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
