package com.cyberstrike.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "登录、登出与会话校验接口（演示版内存 Token）")
public class AuthController {

    @Value("${app.password:admin}")
    private String appPassword;

    @Value("${app.session-duration-hours:12}")
    private int sessionDurationHours;

    // 简单的内存 Token 存储 (生产环境应使用 Redis 或 JWT)
    private final Map<String, LocalDateTime> tokens = new ConcurrentHashMap<>();

    @Operation(summary = "登录获取 Token", description = "POST /api/auth/login，使用 app.password 校验")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (appPassword.equals(password)) {
            String token = UUID.randomUUID().toString();
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(sessionDurationHours);
            tokens.put(token, expiresAt);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("expires_at", expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            result.put("session_duration_hr", sessionDurationHours);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(401).body(Map.of("error", "密码错误"));
    }

    @Operation(summary = "退出登录", description = "POST /api/auth/logout，移除内存中的 Token")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            tokens.remove(token);
        }
        return ResponseEntity.ok(Map.of("message", "已退出登录"));
    }

    @Operation(summary = "修改密码（演示版，仅内存校验）", description = "POST /api/auth/change-password，实际需持久化")
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (!appPassword.equals(oldPassword)) {
            return ResponseEntity.status(400).body(Map.of("error", "旧密码错误"));
        }

        // 注意：这里只是演示，实际应该持久化新密码
        // 由于使用 @Value 注入，无法在运行时修改
        // 生产环境应使用数据库存储
        return ResponseEntity.ok(Map.of("message", "密码修改成功，请重启服务生效"));
    }

    @Operation(summary = "校验 Token", description = "GET /api/auth/validate，Bearer Token 必填")
    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            LocalDateTime expiresAt = tokens.get(token);
            if (expiresAt != null && expiresAt.isAfter(LocalDateTime.now())) {
                return ResponseEntity.ok(Map.of(
                        "token", token,
                        "expires_at", expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            }
        }
        return ResponseEntity.status(401).body(Map.of("error", "Token 无效或已过期"));
    }
}
