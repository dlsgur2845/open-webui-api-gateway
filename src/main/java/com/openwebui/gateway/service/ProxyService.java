package com.openwebui.gateway.service;

import com.openwebui.gateway.security.JwtTokenProvider;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Open-WebUI 백엔드로 요청을 프록시하는 서비스.
 *
 * 프록시 흐름:
 * 1. Gateway JWT에서 userId를 추출 (ProxyController에서 수행)
 * 2. open-webui의 WEBUI_SECRET_KEY로 서명한 초단기(30초) 내부 JWT 생성
 * 3. 해당 JWT를 Authorization 헤더에 담아 open-webui로 요청 전달
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${gateway.openwebui.url}")
    private String openwebuiBaseUrl;

    @Value("${gateway.openwebui.secret-key}")
    private String openwebuiSecretKey;

    private SecretKey openwebuiSigningKey;
    private RestClient restClient;

    /** 프록시 시 제외할 hop-by-hop / 충돌 헤더 */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "authorization", "host", "connection",
            "keep-alive", "transfer-encoding", "te",
            "trailer", "upgrade", "proxy-authorization",
            "proxy-connection", "content-length"
    );

    @PostConstruct
    public void init() {
        this.openwebuiSigningKey = JwtTokenProvider.buildKey(openwebuiSecretKey);

        this.restClient = RestClient.builder()
                .baseUrl(openwebuiBaseUrl)
                .build();

        log.info("프록시 대상 Open-WebUI: {}", openwebuiBaseUrl);
    }

    public ResponseEntity<byte[]> proxyGet(String path, String userId, HttpHeaders incomingHeaders) {
        String internalToken = jwtTokenProvider.createInternalOpenWebuiToken(userId, openwebuiSigningKey);

        return restClient.get()
                .uri(path)
                .headers(headers -> buildHeaders(incomingHeaders, headers, internalToken))
                .retrieve()
                .toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> proxyPost(String path, String userId,
                                             byte[] body, HttpHeaders incomingHeaders) {
        String internalToken = jwtTokenProvider.createInternalOpenWebuiToken(userId, openwebuiSigningKey);

        return restClient.post()
                .uri(path)
                .headers(headers -> buildHeaders(incomingHeaders, headers, internalToken))
                .body(body)
                .retrieve()
                .toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> proxyPut(String path, String userId,
                                            byte[] body, HttpHeaders incomingHeaders) {
        String internalToken = jwtTokenProvider.createInternalOpenWebuiToken(userId, openwebuiSigningKey);

        return restClient.put()
                .uri(path)
                .headers(headers -> buildHeaders(incomingHeaders, headers, internalToken))
                .body(body)
                .retrieve()
                .toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> proxyDelete(String path, String userId, HttpHeaders incomingHeaders) {
        String internalToken = jwtTokenProvider.createInternalOpenWebuiToken(userId, openwebuiSigningKey);

        return restClient.delete()
                .uri(path)
                .headers(headers -> buildHeaders(incomingHeaders, headers, internalToken))
                .retrieve()
                .toEntity(byte[].class);
    }

    /**
     * 원본 헤더를 모두 복사하되,
     * hop-by-hop 헤더와 Authorization은 제외하고 내부 토큰으로 교체한다.
     */
    private void buildHeaders(HttpHeaders incoming, HttpHeaders target, String internalToken) {
        incoming.forEach((key, values) -> {
            if (!EXCLUDED_HEADERS.contains(key.toLowerCase())) {
                target.addAll(key, values);
            }
        });
        target.setBearerAuth(internalToken);
    }
}
