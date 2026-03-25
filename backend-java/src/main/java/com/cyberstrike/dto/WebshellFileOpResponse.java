package com.cyberstrike.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【Go -> Java 迁移新增】WebShell 文件操作响应
 * 对齐 Go 结构体：cyberstrike-ai/internal/handler/webshell.go FileOpResponse
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebshellFileOpResponse {

    /**
     * 是否成功
     */
    private boolean ok;

    /**
     * 操作输出
     */
    private String output;

    /**
     * 错误信息（可选）
     */
    private String error;

    public static WebshellFileOpResponse success(String output) {
        return new WebshellFileOpResponse(true, output, null);
    }

    public static WebshellFileOpResponse error(String error) {
        return new WebshellFileOpResponse(false, null, error);
    }
}
