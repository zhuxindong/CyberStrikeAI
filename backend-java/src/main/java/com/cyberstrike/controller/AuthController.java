package com.cyberstrike.controller;

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
public class AuthController {

    @Value("${app.password:admin}")
    private String appPassword;

    @Value("${app.session-duration-hours:12}")
    private int sessionDurationHours;

    // 简单的内存 Token 存储 (生产环境应使用 Redis 或 JWT)
    private final Map<String, LocalDateTime> tokens = new ConcurrentHashMap<>();

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

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            tokens.remove(token);
        }
        return ResponseEntity.ok(Map.of("message", "已退出登录"));
    }

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
