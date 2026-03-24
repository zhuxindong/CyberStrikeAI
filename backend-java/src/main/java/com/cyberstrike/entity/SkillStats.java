package com.cyberstrike.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【Go -> Java 迁移新增】Skill统计信息
 * 对齐 Go 结构体：cyberstrike-ai/internal/skills/tool.go SkillStats
 */
@Entity
@Table(name = "skill_stats")
@Data
public class SkillStats {

    @Id
    @Column(name = "skill_name")
    private String skillName;

    @Column(name = "total_calls")
    private Integer totalCalls = 0;

    @Column(name = "success_calls")
    private Integer successCalls = 0;

    @Column(name = "failed_calls")
    private Integer failedCalls = 0;

    @Column(name = "last_call_time")
    private LocalDateTime lastCallTime;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 更新时间
     */
    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
