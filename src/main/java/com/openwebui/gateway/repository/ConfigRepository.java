package com.openwebui.gateway.repository;

import com.openwebui.gateway.entity.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ConfigRepository extends JpaRepository<Config, Integer> {

    /**
     * config 테이블에는 1개 행만 존재 — 가장 최근 행을 가져온다.
     * JPQL에는 LIMIT가 없으므로 nativeQuery 사용.
     */
    @Query(value = "SELECT * FROM config ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<Config> findLatest();
}
