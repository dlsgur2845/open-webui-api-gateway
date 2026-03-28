package com.openwebui.gateway.security;

import com.openwebui.gateway.repository.ConfigRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gateway 전용 JWT 토큰 발급 및 검증.
 *
 * 토큰 만료 주기는 기동 시 open-webui의 config 테이블에서 1회 조회하여 캐시한다.
 * 이후에는 캐시된 값을 사용하므로 DB 부하가 없다.
 * 만료 설정이 변경되면 Gateway를 재기동하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final ConfigRepository configRepository;

    @Value("${gateway.jwt.secret}")
    private String secret;

    /** DB에 값이 없을 때 사용할 폴백 (환경변수 또는 기본값) */
    @Value("${gateway.jwt.expires-in-fallback:4w}")
    private String expiresInFallback;

    private SecretKey signingKey;

    /** 기동 시 1회 조회 후 캐시되는 만료 설정값 */
    private String cachedExpiresIn;
    private Duration cachedExpiresDuration;

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(-?\\d+(?:\\.\\d+)?)(ms|s|m|h|d|w)");

    @PostConstruct
    public void init() {
        this.signingKey = buildKey(secret);

        // ── 기동 시 1회 DB 조회 후 캐시 ──
        this.cachedExpiresIn = loadExpiresInFromDb();
        this.cachedExpiresDuration = parseDuration(cachedExpiresIn);

        log.info("Gateway JWT 서명 키 초기화 완료");
        log.info("JWT 만료 설정 캐시 완료: {} (Duration: {})", cachedExpiresIn, cachedExpiresDuration);
    }

    // ================================================================
    //  토큰 발급
    // ================================================================

    /**
     * 로그인 성공 시 Gateway JWT 발급.
     * 캐시된 만료 주기를 사용한다.
     */
    public String createToken(String userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(cachedExpiresDuration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * open-webui 프록시용 내부 JWT 생성 (30초 수명).
     * open-webui의 WEBUI_SECRET_KEY와 동일한 키로 서명한다.
     */
    public String createInternalOpenWebuiToken(String userId, SecretKey openwebuiKey) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(30);

        return Jwts.builder()
                .claim("id", userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(openwebuiKey)
                .compact();
    }

    // ================================================================
    //  토큰 검증
    // ================================================================

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserId(String token) {
        return validateToken(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 토큰: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("유효하지 않은 토큰: {}", e.getMessage());
        }
        return false;
    }

    // ================================================================
    //  캐시된 만료 설정 접근
    // ================================================================

    /** 캐시된 만료 시각(epoch seconds) — 현재 시각 기준 */
    public long getExpiresAtEpochSecond() {
        return Instant.now().plus(cachedExpiresDuration).getEpochSecond();
    }

    /** 캐시된 만료 Duration */
    public Duration getCachedExpiresDuration() {
        return cachedExpiresDuration;
    }

    /** 캐시된 만료 설정 문자열 (예: "4w") */
    public String getCachedExpiresIn() {
        return cachedExpiresIn;
    }

    // ================================================================
    //  기동 시 1회 DB 조회
    // ================================================================

    /**
     * open-webui config 테이블에서 JWT 만료 설정을 조회한다.
     * DB 경로: config.data -> "auth" -> "jwt_expiry"
     *
     * 조회 우선순위:
     *   1. DB config 테이블의 auth.jwt_expiry
     *   2. 환경변수 GATEWAY_JWT_EXPIRES_IN_FALLBACK
     *   3. 하드코딩 기본값 "4w"
     */
    private String loadExpiresInFromDb() {
        try {
            return configRepository.findLatest()
                    .map(config -> {
                        Map<String, Object> data = config.getData();
                        if (data == null) return null;

                        Object authSection = data.get("auth");
                        if (authSection instanceof Map<?, ?> authMap) {
                            Object jwtExpiry = authMap.get("jwt_expiry");
                            if (jwtExpiry != null) {
                                String value = jwtExpiry.toString().trim();
                                if (!value.isEmpty()) {
                                    log.info("DB config 테이블에서 JWT 만료 설정 로드: {}", value);
                                    return value;
                                }
                            }
                        }
                        return null;
                    })
                    .orElseGet(() -> {
                        log.info("DB에 JWT 만료 설정 없음, 폴백 값 사용: {}", expiresInFallback);
                        return expiresInFallback;
                    });
        } catch (Exception e) {
            log.warn("config 테이블 조회 실패, 폴백 값 사용: {}", expiresInFallback, e);
            return expiresInFallback;
        }
    }

    // ================================================================
    //  유틸리티
    // ================================================================

    public static Duration parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return Duration.ofDays(28);
        }

        String trimmed = input.trim();

        if ("-1".equals(trimmed) || "0".equals(trimmed)) {
            return Duration.ofDays(3650);
        }

        Matcher matcher = DURATION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            log.warn("파싱 불가능한 만료 설정 '{}', 기본값 4주 적용", input);
            return Duration.ofDays(28);
        }

        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "ms" -> Duration.ofMillis((long) value);
            case "s"  -> Duration.ofSeconds((long) value);
            case "m"  -> Duration.ofMinutes((long) value);
            case "h"  -> Duration.ofHours((long) value);
            case "d"  -> Duration.ofDays((long) value);
            case "w"  -> Duration.ofDays((long) (value * 7));
            default   -> Duration.ofDays(28);
        };
    }

    static SecretKey buildKey(String raw) {
        byte[] keyBytes = raw.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
