package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysAiModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 大模型数据访问接口
 */
@Repository
public interface AiModelRepository extends JpaRepository<SysAiModel, String> {

    /**
     * 查询唯一的默认大模型
     */
    Optional<SysAiModel> findByIsDefaultTrue();

    /**
     * 查询所有已激活的大模型（按创建时间降序）
     */
    List<SysAiModel> findByIsEnabledTrueOrderByCreatedAtDesc();

    /**
     * 查询所有大模型列表（管理后台使用）
     */
    List<SysAiModel> findAllByOrderByCreatedAtDesc();

    /**
     * 判断是否存在默认大模型
     */
    boolean existsByIsDefaultTrue();

    /**
     * 统计已激活模型数量
     */
    long countByIsEnabledTrue();

    /**
     * 批量重置其他模型的默认状态为 false
     */
    @Modifying
    @Query("UPDATE SysAiModel m SET m.isDefault = false WHERE m.id != :id")
    void clearOtherDefaults(@Param("id") String id);

    /**
     * 重置所有模型的默认状态为 false
     */
    @Modifying
    @Query("UPDATE SysAiModel m SET m.isDefault = false")
    void clearAllDefaults();
}
