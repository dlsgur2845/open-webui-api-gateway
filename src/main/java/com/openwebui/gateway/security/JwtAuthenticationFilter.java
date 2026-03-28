package com.openwebui.gateway.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 매 요청마다 Authorization 헤더의 Bearer 토큰을 검증하여
 * SecurityContext에 인증 정보를 설정하는 필터.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_ROLE = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtTokenProvider.isTokenValid(token)) {
            try {
                Claims claims = jwtTokenProvider.validateToken(token);
                String userId = claims.getSubject();

                // subject(userId)가 없으면 인증 불가
                if (userId == null || userId.isBlank()) {
                    log.warn("JWT에 사용자 ID(sub)가 없습니다");
                    filterChain.doFilter(request, response);
                    return;
                }

                // role이 없으면 기본값 "user"
                String role = claims.get("role", String.class);
                if (role == null || role.isBlank()) {
                    role = DEFAULT_ROLE;
                }

                String email = claims.get("email", String.class);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

                // 요청 속성에 사용자 정보 저장 (프록시에서 활용)
                request.setAttribute("userId", userId);
                request.setAttribute("email", email);
                request.setAttribute("role", role);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.error("JWT 인증 처리 중 오류: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
