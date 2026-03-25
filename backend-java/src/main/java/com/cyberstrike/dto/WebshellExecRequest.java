package com.cyberstrike.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 【Go -> Java 迁移新增】WebShell 命令执行请求
 * 对齐 Go 结构体：cyberstrike-ai/internal/handler/webshell.go ExecRequest
 */
@Data
public class WebshellExecRequest {

    /**
     * WebShell URL（必需）
     */
    @NotBlank(message = "url is required")
    private String url;

    /**
     * 密码/密钥
     */
    private String password;

    /**
     * 类型：php, asp, aspx, jsp, custom
     */
    private String type;

    /**
     * HTTP 方法：GET 或 POST，空则默认 POST
     */
    private String method;

    /**
     * 命令参数名，如 cmd/xxx，空则默认 cmd
     */
    private String cmdParam;

    /**
     * 要执行的命令（必需）
     */
    @NotBlank(message = "command is required")
    private String command;
}
