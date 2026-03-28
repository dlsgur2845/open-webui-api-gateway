package com.openwebui.gateway.config;

import com.openwebui.gateway.dto.ErrorResponse;
import com.openwebui.gateway.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 인증 실패 (잘못된 이메일/비밀번호)
     */
    @ExceptionHandler(AuthService.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthService.AuthenticationException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, e.getMessage()));
    }

    /**
     * 요청 유효성 검증 실패
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("요청 값이 올바르지 않습니다");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, message));
    }

    /**
     * ResponseStatusException (ProxyController의 userId null 등)
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(new ErrorResponse(e.getStatusCode().value(), e.getReason()));
    }

    /**
     * Open-WebUI 프록시 중 네트워크 오류 (연결 불가, 타임아웃)
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleResourceAccessException(ResourceAccessException e) {
        log.error("프록시 네트워크 오류: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ErrorResponse(504, "Open-WebUI 서버에 연결할 수 없습니다"));
    }

    /**
     * Open-WebUI 프록시 중 클라이언트 에러 (4xx)
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientError(HttpClientErrorException e) {
        log.warn("프록시 클라이언트 에러: {} {}", e.getStatusCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatusCode())
                .body(new ErrorResponse(e.getStatusCode().value(),
                        "Open-WebUI 요청 실패: " + e.getStatusText()));
    }

    /**
     * Open-WebUI 프록시 중 서버 에러 (5xx)
     */
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerError(HttpServerErrorException e) {
        log.error("프록시 서버 에러: {} {}", e.getStatusCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatusCode())
                .body(new ErrorResponse(e.getStatusCode().value(),
                        "Open-WebUI 서버 오류"));
    }

    /**
     * 기타 RestClient 예외 (위에서 잡히지 않은 프록시 오류)
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(RestClientException e) {
        log.error("프록시 REST 클라이언트 오류: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "Open-WebUI 서버와 통신 중 오류가 발생했습니다"));
    }

    /**
     * 기타 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("처리되지 않은 예외: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "내부 서버 오류가 발생했습니다"));
    }
}
