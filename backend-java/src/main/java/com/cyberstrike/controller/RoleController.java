package com.cyberstrike.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "安全测试角色管理接口（内存存储示例）")
public class RoleController {

    // 内存存储角色配置 (生产环境应使用数据库)
    private final List<Map<String, Object>> roles = new ArrayList<>();

    public RoleController() {
        // 默认角色
        roles.add(createRole("default", "默认",
                "默认角色，不额外携带用户提示词，使用系统基础能力。"));

        roles.add(createRole("binary_analysis", "二进制分析",
                "二进制分析与利用专家，擅长逆向工程、漏洞挖掘与利用代码编写。"));

        roles.add(createRole("post_exploitation", "后渗透测试",
                "后渗透测试专家，专注于权限维持、横向移动与敏感信息提取。"));

        roles.add(createRole("container_security", "容器安全",
                "容器与Kubernetes安全专家，专注于容器环境逃逸检测与集群安全加固。"));

        roles.add(createRole("pen_tester", "渗透测试",
                "专业渗透测试专家，具备全面深入的漏洞检测能力，熟悉各类攻击手法。"));

        roles.add(createRole("digital_forensics", "数字取证",
                "数字取证与隐写分析专家，擅长文件与内存取证、日志分析与攻击溯源。"));

        roles.add(createRole("info_gathering", "信息收集",
                "资产发现与信息收集专家，精通OSINT技术，全面描绘目标攻击面。"));

        roles.add(createRole("cloud_audit", "云安全审计",
                "云安全审计专家，专注于云平台配配置检查、IAM权限评估与合规性审计。"));
    }

    private Map<String, Object> createRole(String id, String name, String systemPrompt) {
        Map<String, Object> role = new HashMap<>();
        role.put("id", id);
        role.put("name", name);
        role.put("systemPrompt", systemPrompt);
        role.put("isDefault", "security_tester".equals(id));
        return role;
    }

    @Operation(summary = "列出全部角色", description = "GET /api/roles")
    @GetMapping
    public ResponseEntity<?> listRoles() {
        return ResponseEntity.ok(roles);
    }

    @Operation(summary = "获取角色详情", description = "GET /api/roles/{id}")
    @GetMapping("/{id}")
    public ResponseEntity<?> getRole(@Parameter(description = "角色ID") @PathVariable String id) {
        return roles.stream()
                .filter(r -> id.equals(r.get("id")))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "创建角色", description = "POST /api/roles 内存示例，生产应持久化")
    @PostMapping
    public ResponseEntity<?> createRole(@RequestBody Map<String, String> body) {
        Map<String, Object> role = createRole(
                UUID.randomUUID().toString(),
                body.get("name"),
                body.get("systemPrompt"));
        roles.add(role);
        return ResponseEntity.ok(role);
    }

    @Operation(summary = "更新角色", description = "PUT /api/roles/{id}")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRole(@Parameter(description = "角色ID") @PathVariable String id, @RequestBody Map<String, String> body) {
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

    @Operation(summary = "删除角色", description = "DELETE /api/roles/{id}")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRole(@Parameter(description = "角色ID") @PathVariable String id) {
        boolean removed = roles.removeIf(r -> id.equals(r.get("id")));
        if (removed) {
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }
}
