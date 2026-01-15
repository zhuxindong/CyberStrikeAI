package com.cyberstrike.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    // 内存存储角色配置 (生产环境应使用数据库)
    private final List<Map<String, Object>> roles = new ArrayList<>();

    public RoleController() {
        // 默认角色
        roles.add(createRole("security_tester", "安全测试员",
                "你是一个专业的安全测试员，负责对目标系统进行渗透测试和漏洞评估。"));
        roles.add(createRole("red_team", "红队成员",
                "你是红队成员，模拟真实攻击者的行为，尝试突破安全防线。"));
        roles.add(createRole("blue_team", "蓝队成员",
                "你是蓝队成员，负责检测和响应安全威胁，加固系统防御。"));
        roles.add(createRole("vulnerability_analyst", "漏洞分析师",
                "你是漏洞分析师，专门分析和评估发现的安全漏洞。"));
        roles.add(createRole("general", "通用助手",
                "你是通用安全助手，可以回答各种安全相关的问题。"));
    }

    private Map<String, Object> createRole(String id, String name, String systemPrompt) {
        Map<String, Object> role = new HashMap<>();
        role.put("id", id);
        role.put("name", name);
        role.put("systemPrompt", systemPrompt);
        role.put("isDefault", "security_tester".equals(id));
        return role;
    }

    @GetMapping
    public ResponseEntity<?> listRoles() {
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRole(@PathVariable String id) {
        return roles.stream()
                .filter(r -> id.equals(r.get("id")))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createRole(@RequestBody Map<String, String> body) {
        Map<String, Object> role = createRole(
                UUID.randomUUID().toString(),
                body.get("name"),
                body.get("systemPrompt"));
        roles.add(role);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRole(@PathVariable String id, @RequestBody Map<String, String> body) {
        return roles.stream()
                .filter(r -> id.equals(r.get("id")))
                .findFirst()
                .map(role -> {
                    if (body.containsKey("name"))
                        role.put("name", body.get("name"));
                    if (body.containsKey("systemPrompt"))
                        role.put("systemPrompt", body.get("systemPrompt"));
                    return ResponseEntity.ok(role);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable String id) {
        boolean removed = roles.removeIf(r -> id.equals(r.get("id")));
        if (removed) {
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }
}
