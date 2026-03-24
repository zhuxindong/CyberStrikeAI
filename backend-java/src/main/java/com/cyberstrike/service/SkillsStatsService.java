package com.cyberstrike.service;

import com.cyberstrike.entity.SkillStats;
import com.cyberstrike.repository.SkillStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 【Go -> Java 迁移】Skills统计服务
 * 对齐 Go 实现：internal/database/skill_stats.go
 */
@Service
public class SkillsStatsService {

    private static final Logger logger = LoggerFactory.getLogger(SkillsStatsService.class);

    @Autowired
    private SkillStatsRepository skillStatsRepository;

    /**
     * 更新Skills统计信息（累加模式）
     * 对齐 Go：db.UpdateSkillStats
     */
    @Transactional
    public void updateSkillStats(String skillName, int totalCalls, int successCalls, int failedCalls) {
        Optional<SkillStats> existing = skillStatsRepository.findById(skillName);
        
        SkillStats stats;
        if (existing.isPresent()) {
            stats = existing.get();
            stats.setTotalCalls(stats.getTotalCalls() + totalCalls);
            stats.setSuccessCalls(stats.getSuccessCalls() + successCalls);
            stats.setFailedCalls(stats.getFailedCalls() + failedCalls);
        } else {
            stats = new SkillStats();
            stats.setSkillName(skillName);
            stats.setTotalCalls(totalCalls);
            stats.setSuccessCalls(successCalls);
            stats.setFailedCalls(failedCalls);
        }
        
        stats.setLastCallTime(LocalDateTime.now());
        
        try {
            skillStatsRepository.save(stats);
            logger.info("Skills统计信息已更新 - skill: {}, totalCalls: {}, successCalls: {}, failedCalls: {}", 
                skillName, totalCalls, successCalls, failedCalls);
        } catch (Exception e) {
            logger.error("保存Skills统计信息失败: {}", e.getMessage());
        }
    }

    /**
     * 记录skill调用（成功）
     * 对齐 Go：tool.go readSkillHandler
     */
    public void recordSkillCall(String skillName, boolean success) {
        int totalCalls = 1;
        int successCalls = success ? 1 : 0;
        int failedCalls = success ? 0 : 1;
        
        updateSkillStats(skillName, totalCalls, successCalls, failedCalls);
    }

    /**
     * 加载所有Skills统计信息
     * 对齐 Go：db.LoadSkillStats
     */
    public List<SkillStats> loadAllSkillStats() {
        try {
            return skillStatsRepository.findAll();
        } catch (Exception e) {
            logger.error("加载Skills统计信息失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取单个skill的统计信息
     */
    public Optional<SkillStats> getSkillStats(String skillName) {
        return skillStatsRepository.findById(skillName);
    }

    /**
     * 获取skills调用统计信息（包含所有skills，即使没有调用记录）
     * 对齐 Go：handler GetSkillStats
     */
    public Map<String, Object> getSkillStats(String skillsDir, List<String> allSkillNames) {
        // 从数据库加载调用统计
        Map<String, SkillStats> skillStatsMap = new HashMap<>();
        try {
            List<SkillStats> allStats = skillStatsRepository.findAll();
            for (SkillStats stat : allStats) {
                skillStatsMap.put(stat.getSkillName(), stat);
            }
        } catch (Exception e) {
            logger.warn("从数据库加载Skills统计信息失败: {}", e.getMessage());
        }

        // 构建统计信息（包含所有skills，即使没有调用记录）
        List<Map<String, Object>> statsList = new ArrayList<>();
        int totalCalls = 0;
        int totalSuccess = 0;
        int totalFailed = 0;

        for (String skillName : allSkillNames) {
            SkillStats stat = skillStatsMap.get(skillName);
            if (stat == null) {
                stat = new SkillStats();
                stat.setSkillName(skillName);
                stat.setTotalCalls(0);
                stat.setSuccessCalls(0);
                stat.setFailedCalls(0);
            }

            totalCalls += stat.getTotalCalls();
            totalSuccess += stat.getSuccessCalls();
            totalFailed += stat.getFailedCalls();

            String lastCallTimeStr = "";
            if (stat.getLastCallTime() != null) {
                lastCallTimeStr = stat.getLastCallTime().toString();
            }

            Map<String, Object> statInfo = new HashMap<>();
            statInfo.put("skill_name", stat.getSkillName());
            statInfo.put("total_calls", stat.getTotalCalls());
            statInfo.put("success_calls", stat.getSuccessCalls());
            statInfo.put("failed_calls", stat.getFailedCalls());
            statInfo.put("last_call_time", lastCallTimeStr);
            statsList.add(statInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total_skills", allSkillNames.size());
        result.put("total_calls", totalCalls);
        result.put("total_success", totalSuccess);
        result.put("total_failed", totalFailed);
        result.put("skills_dir", skillsDir);
        result.put("stats", statsList);

        return result;
    }

    /**
     * 清空所有Skills统计信息
     * 对齐 Go：db.ClearSkillStats
     */
    @Transactional
    public void clearAllSkillStats() {
        try {
            skillStatsRepository.deleteAll();
            logger.info("已清空所有Skills统计信息");
        } catch (Exception e) {
            logger.error("清空Skills统计信息失败: {}", e.getMessage());
            throw new RuntimeException("清空统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 清空指定skill的统计信息
     * 对齐 Go：db.ClearSkillStatsByName
     */
    @Transactional
    public void clearSkillStatsByName(String skillName) {
        try {
            skillStatsRepository.deleteById(skillName);
            logger.info("已清空指定skill统计信息: {}", skillName);
        } catch (Exception e) {
            logger.error("清空指定skill统计信息失败: {}", e.getMessage());
            throw new RuntimeException("清空统计信息失败: " + e.getMessage());
        }
    }
}
