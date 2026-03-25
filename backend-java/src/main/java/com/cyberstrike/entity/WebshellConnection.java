package com.cyberstrike.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【Go -> Java 迁移新增】WebShell 连接配置（仅资产登记/配置管理，不包含远程命令执行）。
 *
 * 对齐 Go 结构体：cyberstrike-ai/internal/database/webshell.go WebShellConnection
 */
@Entity
@Table(name = "webshell_connections")
@Data
public class WebshellConnection {

    @Id
    private String id;

    @Column(nullable = false)
    private String url;

    /**
     * 注意：这里保留 password 字段是为了兼容 Go 版数据模型。
     * 安全建议：生产环境应加密存储或使用凭据系统（Vault/KMS）。
     */
    private String password;

    /**
     * php, asp, aspx, jsp, custom（与 Go 版约定一致）
     */
    private String type;

    /**
     * get / post（与 Go 版一致）
     */
    private String method;

    /**
     * 命令参数名（Go 版字段：cmd_param；JSON：cmdParam）
     */
    @Column(name = "cmd_param")
    private String cmdParam;

    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
