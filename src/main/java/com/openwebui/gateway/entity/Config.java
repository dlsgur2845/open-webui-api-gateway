package com.openwebui.gateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Open-WebUI의 config 테이블 매핑 (읽기 전용).
 *
 * config 테이블에는 단 1개의 행만 존재하며,
 * 전체 설정이 data(JSON) 컬럼에 중첩 구조로 저장된다.
 *
 * JWT 만료 설정 경로: data -> "auth" -> "jwt_expiry"
 * 예: { "auth": { "jwt_expiry": "4w" }, "ui": { ... } }
 */
@Entity
@Table(name = "config")
@Getter
@NoArgsConstructor
public class Config {

    @Id
    @Column(name = "id")
    private Integer id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data;

    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
