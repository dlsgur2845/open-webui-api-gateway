package com.openwebui.gateway.controller;

import com.openwebui.gateway.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Open-WebUI API 프록시 컨트롤러.
 *
 * /api/v1/openwebui/** 로 들어오는 모든 요청을
 * Gateway JWT 검증 후 open-webui 백엔드로 전달한다.
 *
 * 예시:
 *   GET  /api/v1/openwebui/api/models          → open-webui GET /api/models
 *   POST /api/v1/openwebui/api/chat/completions → open-webui POST /api/chat/completions
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/openwebui")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    @GetMapping("/**")
    public ResponseEntity<byte[]> proxyGet(HttpServletRequest request,
                                           @RequestHeader HttpHeaders headers) {
        String userId = extractUserId(request);
        String targetPath = extractTargetPath(request);

        log.debug("프록시 GET: {} → {}", request.getRequestURI(), targetPath);
        return proxyService.proxyGet(targetPath, userId, headers);
    }

    @PostMapping("/**")
    public ResponseEntity<byte[]> proxyPost(HttpServletRequest request,
                                            @RequestHeader HttpHeaders headers) throws IOException {
        String userId = extractUserId(request);
        String targetPath = extractTargetPath(request);
        byte[] body = request.getInputStream().readAllBytes();

        log.debug("프록시 POST: {} → {} (body {} bytes)", request.getRequestURI(), targetPath, body.length);
        return proxyService.proxyPost(targetPath, userId, body, headers);
    }

    @PutMapping("/**")
    public ResponseEntity<byte[]> proxyPut(HttpServletRequest request,
                                           @RequestHeader HttpHeaders headers) throws IOException {
        String userId = extractUserId(request);
        String targetPath = extractTargetPath(request);
        byte[] body = request.getInputStream().readAllBytes();

        log.debug("프록시 PUT: {} → {} (body {} bytes)", request.getRequestURI(), targetPath, body.length);
        return proxyService.proxyPut(targetPath, userId, body, headers);
    }

    @DeleteMapping("/**")
    public ResponseEntity<byte[]> proxyDelete(HttpServletRequest request,
                                              @RequestHeader HttpHeaders headers) {
        String userId = extractUserId(request);
        String targetPath = extractTargetPath(request);

        log.debug("프록시 DELETE: {} → {}", request.getRequestURI(), targetPath);
        return proxyService.proxyDelete(targetPath, userId, headers);
    }

    /**
     * JwtAuthFilter가 설정한 userId 속성을 가져온다.
     * 인증 필터를 통과했으면 반드시 존재해야 한다.
     */
    private String extractUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다");
        }
        return userId;
    }

    /**
     * /api/v1/openwebui/xxx/yyy → /xxx/yyy (쿼리 스트링 포함)
     */
    private String extractTargetPath(HttpServletRequest request) {
        String prefix = "/api/v1/openwebui";
        String uri = request.getRequestURI();
        String path = uri.substring(prefix.length());

        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            path = path + "?" + queryString;
        }

        return path;
    }
}
