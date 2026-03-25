package com.cyberstrike.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 【Go -> Java 迁移新增】WebShell 文件操作请求
 * 对齐 Go 结构体：cyberstrike-ai/internal/handler/webshell.go FileOpRequest
 */
@Data
public class WebshellFileOpRequest {

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
     * 操作类型：list, read, delete, write, mkdir, rename, upload, upload_chunk
     */
    @NotBlank(message = "action is required")
    private String action;

    /**
     * 路径（list/read/delete/mkdir/rename/upload 时使用）
     */
    private String path;

    /**
     * 重命名时的目标路径
     */
    private String targetPath;

    /**
     * 写入/上传时的内容
     */
    private String content;

    /**
     * 分片上传时的块索引（upload_chunk 时，0 表示首块）
     */
    private Integer chunkIndex;
}
