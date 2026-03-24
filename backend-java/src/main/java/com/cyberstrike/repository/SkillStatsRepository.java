package com.cyberstrike.repository;

import com.cyberstrike.entity.SkillStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 【Go -> Java 迁移新增】Skill统计仓库
 */
@Repository
public interface SkillStatsRepository extends JpaRepository<SkillStats, String> {
}
