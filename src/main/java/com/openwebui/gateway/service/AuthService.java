package com.openwebui.gateway.service;

import com.openwebui.gateway.dto.LoginRequest;
import com.openwebui.gateway.dto.LoginResponse;
import com.openwebui.gateway.entity.Auth;
import com.openwebui.gateway.entity.User;
import com.openwebui.gateway.repository.AuthRepository;
import com.openwebui.gateway.repository.UserRepository;
import com.openwebui.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * 로그인 처리.
     * 1) 이메일로 User 조회
     * 2) Auth 테이블에서 해당 사용자의 활성 계정 조회
     * 3) bcrypt 비밀번호 검증
     * 4) Gateway JWT 토큰 발급
     */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();

        // 1. 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다"));

        // 2. 활성 Auth 레코드 조회
        Auth auth = authRepository.findByIdAndActiveTrue(user.getId())
                .orElseThrow(() -> new AuthenticationException("비활성화된 계정입니다"));

        // 3. 저장된 비밀번호 null 체크
        if (auth.getPassword() == null || auth.getPassword().isBlank()) {
            log.warn("비밀번호가 설정되지 않은 계정: userId={}", user.getId());
            throw new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        // 4. 비밀번호 검증 (bcrypt, 72바이트 제한 적용)
        String passwordToCheck = truncatePasswordForBcrypt(request.password());
        if (!passwordEncoder.matches(passwordToCheck, auth.getPassword())) {
            throw new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        // 5. Gateway JWT 발급
        String token = jwtTokenProvider.createToken(user.getId(), user.getEmail(), user.getRole());
        long expiresAt = jwtTokenProvider.getExpiresAtEpochSecond();

        log.info("로그인 성공: userId={}, email={}", user.getId(), user.getEmail());

        return new LoginResponse(
                token,
                expiresAt,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getProfileImageUrl()
        );
    }

    /**
     * bcrypt는 최대 72바이트까지만 해시한다.
     * open-webui와 동일하게 72바이트 초과 시 자른다.
     */
    private String truncatePasswordForBcrypt(String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > BCRYPT_MAX_BYTES) {
            return new String(bytes, 0, BCRYPT_MAX_BYTES, StandardCharsets.UTF_8);
        }
        return password;
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
