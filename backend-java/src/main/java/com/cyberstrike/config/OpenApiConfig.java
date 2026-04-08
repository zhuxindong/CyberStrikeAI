package com.cyberstrike.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.openapi.title:CyberStrikeAI API}")
    private String title;

    @Value("${app.openapi.description:AI驱动的自动化安全测试平台API文档}")
    private String description;

    @Value("${app.openapi.version:1.0.0}")
    private String version;

    @Bean
    public OpenAPI customOpenAPI(@Value("${server.port:8080}") int port,
                                 @Value("${app.server-url:}") String serverUrl) {
        String url = (serverUrl != null && !serverUrl.isBlank())
                ? serverUrl
                : "http://localhost:" + port;

//        SecurityScheme bearer = new SecurityScheme()
//                .type(SecurityScheme.Type.HTTP)
//                .scheme("bearer")
//                .bearerFormat("JWT")
//                .description("使用Bearer Token进行认证。Token通过 /api/auth/login 接口获取。");

        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact().name("CyberStrikeAI")))
                .servers(List.of(new Server().url(url).description("当前服务器")))
//                .components(new Components().addSecuritySchemes("bearerAuth", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
