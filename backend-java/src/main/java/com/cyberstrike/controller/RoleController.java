package com.cyberstrike.controller;

import com.cyberstrike.dto.RoleDTO;
import com.cyberstrike.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "安全测试角色管理接口")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "获取所有角色")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRoles() {
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(Map.of("roles", roles));
    }

    @Operation(summary = "获取单个角色")
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getRole(
            @Parameter(description = "角色名称") @PathVariable String name) {
        RoleDTO role = roleService.getRole(name);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("role", role));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{name}")
    public ResponseEntity<Map<String, Object>> updateRole(
            @Parameter(description = "角色名称") @PathVariable String name,
            @RequestBody RoleDTO request) {
        try {
            RoleDTO updated = roleService.updateRole(name, request);
            return ResponseEntity.ok(Map.of(
                    "message", "角色已更新",
                    "role", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("更新角色失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "保存配置失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "创建角色")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRole(@RequestBody RoleDTO request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "角色名称不能为空"));
        }

        try {
            RoleDTO created = roleService.createRole(request);
            return ResponseEntity.ok(Map.of(
                    "message", "角色已创建",
                    "role", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("创建角色失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "保存配置失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> deleteRole(
            @Parameter(description = "角色名称") @PathVariable String name) {
        try {
            roleService.deleteRole(name);
            return ResponseEntity.ok(Map.of("message", "角色已删除"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("删除角色失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }
}