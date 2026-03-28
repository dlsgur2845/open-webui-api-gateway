package com.openwebui.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Open-WebUI의 auth 테이블 매핑 (읽기 전용).
 * id는 User.id와 동일한 값을 가진다.
 * password는 bcrypt 해시.
 */
@Entity
@Table(name = "auth")
@Getter
@NoArgsConstructor
public class Auth {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "email")
    private String email;

    @Column(name = "password", columnDefinition = "TEXT")
    private String password;

    @Column(name = "active")
    private Boolean active;
}
