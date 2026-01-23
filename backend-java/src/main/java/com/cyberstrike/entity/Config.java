package com.cyberstrike.entity;


import jakarta.persistence.*;

/**
 * 系统配置实体类
 */
@Entity
@Table(name = "config")
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

    // ==================== Getter 和 Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    @Override
    public String toString() {
        return "Config{" +
                "id=" + id +
                ", apiKey='" + apiKey + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", model='" + model + '\'' +
                ", maxIterations=" + maxIterations +
                ", language='" + language + '\'' +
                ", theme='" + theme + '\'' +
                '}';
    }
}