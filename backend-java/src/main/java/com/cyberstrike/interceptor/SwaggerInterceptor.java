package com.cyberstrike.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


@Component
public class SwaggerInterceptor implements HandlerInterceptor {

    @Value("${app.swagger-token:}")
    private String validToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 只拦截 swagger 相关路径
        if (path.contains("/swagger") || path.contains("/v3/api-docs")) {
            String token = request.getHeader("token");
            if (token == null) {
                token = request.getParameter("token");
            }

            if (validToken != null && !validToken.isEmpty() && validToken.equals(token)) {
                return true;
            }

            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":当前页面不可访问\"}");
            return false;
        }

        return true;
    }
}