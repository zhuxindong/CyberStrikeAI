package com.cyberstrike.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


@Component
public class SwaggerInterceptor implements HandlerInterceptor {

    @Value("${app.swagger.token:}")
    private String validToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 放行静态资源
        if (path.contains("/webjars/") ||
                path.contains("/swagger-ui/") && path.endsWith(".js") ||
                path.contains("/swagger-ui/") && path.endsWith(".css")) {
            return true;
        }

        // 只拦截 HTML 页面
        if (path.equals("/swagger-ui.html") || path.equals("/swagger-ui/index.html")) {
            String token = request.getParameter("token");
            if (validToken != null && validToken.equals(token)) {
                return true;
            }
            response.setStatus(401);
            response.getWriter().write("Token required");
            return false;
        }

        return true;
    }
}