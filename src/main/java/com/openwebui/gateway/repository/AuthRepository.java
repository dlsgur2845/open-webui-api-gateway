package com.openwebui.gateway.repository;

import com.openwebui.gateway.entity.Auth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthRepository extends JpaRepository<Auth, String> {

    Optional<Auth> findByIdAndActiveTrue(String id);
}
