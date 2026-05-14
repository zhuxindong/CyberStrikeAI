package com.cyberstrike.service;

import com.cyberstrike.tool.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;

    @Value("${cyberstrike.auth.username:admin}")
    private String validUsername;

    @Value("${cyberstrike.auth.password:admin}")
    private String validPassword;

    public String login(String username, String password) {
        if (validUsername.equals(username) && validPassword.equals(password)) {
            String token = jwtUtil.generateToken(username);
            log.info("用户登录成功: {}", username);
            return token;
        }
        log.warn("登录失败: {}", username);
        return null;
    }

    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    public String getUsernameFromToken(String token) {
        return jwtUtil.getUsernameFromToken(token);
    }
}