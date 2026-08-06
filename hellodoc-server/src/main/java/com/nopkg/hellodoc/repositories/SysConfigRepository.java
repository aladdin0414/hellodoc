package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {
    Optional<SysConfig> findByConfigKey(String key);

    List<SysConfig> findByConfigGroup(String group);

    List<SysConfig> findByIsFrontendTrue();

    boolean existsByConfigKey(String key);
}
