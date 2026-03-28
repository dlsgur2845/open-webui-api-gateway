package com.openwebui.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Open-WebUI의 user 테이블 매핑 (읽기 전용).
 * open-webui가 소유한 테이블이므로 이 엔티티로 DDL을 생성하지 않는다.
 */
@Entity
@Table(name = "\"user\"")  // PostgreSQL 예약어이므로 인용 부호 사용
@Getter
@NoArgsConstructor
public class User {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "email")
    private String email;

    @Column(name = "name")
    private String name;

    @Column(name = "role")
    private String role;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "last_active_at")
    private Long lastActiveAt;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;
}
