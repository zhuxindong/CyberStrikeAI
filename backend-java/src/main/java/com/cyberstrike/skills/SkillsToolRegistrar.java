package com.cyberstrike.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 【Go -> Java 迁移】Skills工具注册器
 * 
 * 注意：Skills工具的注册现在在ToolRegistry构造函数中完成
 * 此组件保留用于提供SkillsManager的访问
 */
@Component
public class SkillsToolRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(SkillsToolRegistrar.class);

    private final SkillsManager skillsManager;

    public SkillsToolRegistrar(SkillsManager skillsManager) {
        this.skillsManager = skillsManager;
        logger.info("SkillsToolRegistrar 初始化完成");
    }

    public SkillsManager getSkillsManager() {
        return skillsManager;
    }
}
