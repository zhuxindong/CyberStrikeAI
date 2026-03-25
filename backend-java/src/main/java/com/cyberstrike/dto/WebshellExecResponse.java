package com.cyberstrike.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【Go -> Java 迁移新增】WebShell 命令执行响应
 * 对齐 Go 结构体：cyberstrike-ai/internal/handler/webshell.go ExecResponse
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebshellExecResponse {

    /**
     * 是否成功（HTTP 状态码为 200）
     */
    private boolean ok;

    /**
     * 命令执行输出
     */
    private String output;

    /**
     * 错误信息（可选）
     */
    private String error;

    /**
     * HTTP 响应码（可选）
     */
    private Integer httpCode;

    public static WebshellExecResponse success(String output) {
        return new WebshellExecResponse(true, output, null, 200);
    }

    public static WebshellExecResponse success(String output, int httpCode) {
        return new WebshellExecResponse(httpCode == 200, output, null, httpCode);
    }

    public static WebshellExecResponse error(String error) {
        return new WebshellExecResponse(false, null, error, null);
    }
}
