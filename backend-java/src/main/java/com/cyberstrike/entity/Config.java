package com.cyberstrike.entity;


import jakarta.persistence.*;
import lombok.Data;

/**
 * 系统配置实体类
 */
@Entity
@Table(name = "config")
@Data
public class Config {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * API密钥
     */
    @Column(name = "api_key", length = 255)
    private String apiKey;

    /**
     * 基础URL
     */
    @Column(name = "base_url", length = 255)
    private String baseUrl;

    /**
     * 模型名称
     */
    @Column(name = "model", length = 255)
    private String model;

    /**
     * 最大迭代次数
     */
    @Column(name = "max_iterations")
    private Integer maxIterations;

    /**
     * 语言设置
     */
    @Column(name = "language", length = 10)
    private String language;

    /**
     * 主题设置
     */
    @Column(name = "theme", length = 10)
    private String theme;

    /**
     * API密钥
     */
    @Column(name = "em_api_key", length = 255)
    private String emApiKey;

    /**
     * 基础URL
     */
    @Column(name = "em_base_url", length = 255)
    private String emBaseUrl;

    /**
     * 模型名称
     */
    @Column(name = "em_model", length = 255)
    private String emModel;

}