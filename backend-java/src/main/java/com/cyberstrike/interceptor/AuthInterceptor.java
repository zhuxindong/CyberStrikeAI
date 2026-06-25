package com.cyberstrike.interceptor;

import com.cyberstrike.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Value("${app.swagger.token:}")
    private String swaggerToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // ========== Swagger 页面访问控制 ==========
        // 放行静态资源
        if (path.contains("/webjars/") ||
                (path.contains("/swagger-ui/") && path.endsWith(".js")) ||
                (path.contains("/swagger-ui/") && path.endsWith(".css"))) {
            return true;
        }

        // Swagger HTML 页面需要 token 验证
//        if (path.equals("/swagger-ui.html") || path.equals("/swagger-ui/index.html")) {
//            String token = request.getParameter("token");
//            if (swaggerToken != null && !swaggerToken.isEmpty() && swaggerToken.equals(token)) {
//                return true;
//            }
//            response.setStatus(401);
//            response.setContentType("application/json;charset=UTF-8");
//            response.getWriter().write("{\"error\":\"Token required for Swagger access\"}");
//            return false;
//        }
        // 拦截 Swagger 主页面，要求携带 token
        if (path.equals("/swagger-ui.html") || path.equals("/swagger-ui/index.html") || path.equals("/swagger-ui/")) {
            String token = null;

            // 方式1：从 Authorization Header 获取
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // 方式2：从 URL 参数获取（支持前端跳转）
            if (token == null) {
                token = request.getParameter("token");
            }

            // 验证 Token
            if (token == null || !authService.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"请先登录后再访问 Swagger 文档\"}");
                return false;
            }
            return true;
        }

        // ========== API 认证 ==========
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 放行登录接口
        if (path.equals("/api/auth/login")) {
            return true;
        }

        // 只拦截 /api/ 开头的请求
        if (path.startsWith("/api/")) {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"未授权访问，请先登录\"}");
                return false;
            }

            String token = authHeader.substring(7);

            if (!authService.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Token 无效或已过期，请重新登录\"}");
                return false;
            }
        }

        return true;
    }
}