package com.cyberstrike.skills;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【Go -> Java 迁移】Skill定义
 * 对齐 Go 结构体：cyberstrike-ai/internal/skills/manager.go Skill
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    /**
     * Skill名称
     */
    private String name;

    /**
     * Skill描述
     */
    private String description;

    /**
     * Skill内容（从SKILL.md中提取）
     */
    private String content;

    /**
     * Skill路径
     */
    private String path;
}
