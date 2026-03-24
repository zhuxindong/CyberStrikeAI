package com.cyberstrike.controller;

import com.cyberstrike.skills.Skill;
import com.cyberstrike.skills.SkillsManager;
import com.cyberstrike.service.SkillsStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 【Go -> Java 迁移】Skills REST API控制器
 * 对齐 Go 实现：internal/handler/skills.go
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private static final Logger logger = LoggerFactory.getLogger(SkillsController.class);

    @Autowired
    private SkillsManager skillsManager;

    @Autowired
    private SkillsStatsService skillsStatsService;

    @Value("${security.skills-dir:skills}")
    private String skillsDir;

    /**
     * 获取所有skills列表（支持分页和搜索）
     * GET /api/skills?search=&limit=20&offset=0
     * 对齐 Go：handler GetSkills
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSkills(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page) {

        // 参数校验
        if (size > 10000) {
            size = 10000;
        }
        if (size < 1) {
            size = 20;
        }
        if (page < 1) {
            page = 1;
        }

        Map<String, Object> result = skillsManager.getSkills(search, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取单个skill的详细信息
     * GET /api/skills/{name}
     * 对齐 Go：handler GetSkill
     */
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String name) {
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "skill名称不能为空"));
        }
        
        Map<String, Object> skill = skillsManager.getSkill(name);
        if (skill == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "skill不存在"));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("skill", skill);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取绑定指定skill的角色列表
     * GET /api/skills/{name}/roles
     * 对齐 Go：handler GetSkillBoundRoles
     */
    @GetMapping("/{name}/roles")
    public ResponseEntity<Map<String, Object>> getSkillBoundRoles(@PathVariable String name) {
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "skill名称不能为空"));
        }
        
        // 暂时返回空列表，后续可从配置中获取角色绑定信息
        List<String> boundRoles = Collections.emptyList();
        
        Map<String, Object> result = new HashMap<>();
        result.put("skill", name);
        result.put("bound_roles", boundRoles);
        result.put("bound_count", boundRoles.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 创建新skill
     * POST /api/skills
     * 对齐 Go：handler CreateSkill
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSkill(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String description = request.get("description");
        String content = request.get("content");
        
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的请求参数: name不能为空"));
        }
        
        if (content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的请求参数: content不能为空"));
        }
        
        // 验证skill名称
        if (!skillsManager.isValidSkillName(name)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "skill名称只能包含字母、数字、连字符和下划线"));
        }
        
        Map<String, Object> result = skillsManager.createSkill(name, description, content);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "创建skill失败"));
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 更新skill
     * PUT /api/skills/{name}
     * 对齐 Go：handler UpdateSkill
     */
    @PutMapping("/{name}")
    public ResponseEntity<Map<String, Object>> updateSkill(
            @PathVariable String name,
            @RequestBody Map<String, String> request) {
        
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "skill名称不能为空"));
        }
        
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的请求参数: content不能为空"));
        }
        
        String description = request.get("description");
        
        Map<String, Object> result = skillsManager.updateSkill(name, description, content);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "skill不存在"));
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 删除skill
     * DELETE /api/skills/{name}
     * 对齐 Go：handler DeleteSkill
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> deleteSkill(@PathVariable String name) {
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "skill名称不能为空"));
        }
        
        Map<String, Object> result = skillsManager.deleteSkill(name);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "skill不存在"));
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取skills调用统计信息
     * GET /api/skills/stats
     * 对齐 Go：handler GetSkillStats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSkillStats() {
        List<String> skillList = skillsManager.listSkills();
        
        if (skillList.isEmpty()) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("total_skills", 0);
            emptyResult.put("total_calls", 0);
            emptyResult.put("total_success", 0);
            emptyResult.put("total_failed", 0);
            emptyResult.put("skills_dir", skillsDir);
            emptyResult.put("stats", Collections.emptyList());
            return ResponseEntity.ok(emptyResult);
        }
        
        Map<String, Object> result = skillsStatsService.getSkillStats(skillsDir, skillList);
        return ResponseEntity.ok(result);
    }

    /**
     * 清空所有Skills统计信息
     * DELETE /api/skills/stats
     * 对齐 Go：handler ClearSkillStats
     */
    @DeleteMapping("/stats")
    public ResponseEntity<Map<String, Object>> clearSkillStats() {
        try {
            skillsStatsService.clearAllSkillStats();
            Map<String, Object> result = new HashMap<>();
            result.put("message", "已清空所有Skills统计信息");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "清空统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 清空指定skill的统计信息
     * DELETE /api/skills/stats/{name}
     * 对齐 Go：handler ClearSkillStatsByName
     */
    @DeleteMapping("/stats/{name}")
    public ResponseEntity<Map<String, Object>> clearSkillStatsByName(@PathVariable String name) {
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "skill名称不能为空"));
        }
        
        try {
            skillsStatsService.clearSkillStatsByName(name);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "已清空skill '" + name + "' 的统计信息");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "清空统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取skill的完整内容（用于测试/调试）
     * GET /api/skills/{name}/content
     */
    @GetMapping("/{name}/content")
    public ResponseEntity<Map<String, Object>> getSkillContent(@PathVariable String name) {
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "skill名称不能为空"));
        }
        
        Skill skill = skillsManager.loadSkill(name);
        if (skill == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "skill不存在"));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("name", skill.getName());
        result.put("description", skill.getDescription());
        result.put("content", skill.getContent());
        result.put("path", skill.getPath());
        
        return ResponseEntity.ok(result);
    }

}
