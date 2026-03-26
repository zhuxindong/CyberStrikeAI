package com.cyberstrike.service;

import com.cyberstrike.entity.Config;
import com.cyberstrike.repository.ConfigRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 向量嵌入服务
 * 模仿 OpenAiService 的实现方式
 */
@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    // --- 1. 核心：将 RestClient 定义为成员变量 (单例模式) ---
    private volatile RestClient restClient;

    private final ConfigRepository configRepository;
    private final RestClient.Builder restClientBuilder;

    private volatile String currentModel = "text-embedding-ada-002";

    // --- 2. 注入 Builder ---
    @Autowired
    public EmbeddingService(ConfigRepository configRepository, RestClient.Builder restClientBuilder) {
        this.configRepository = configRepository;
        this.restClientBuilder = restClientBuilder;
        this.restClient = buildRestClient(); // 首次启动时构建
    }

    /**
     * 构建 RestClient 实例
     */
    private RestClient buildRestClient() {

        // --- A. 初始化配置 ---
        Config config = configRepository.findById(1L).orElse(null);
        String baseUrl = "https://api.openai.com";
        String apiKey = System.getenv("OPENAI_API_KEY");

        if (config != null) {
            if (config.getEmBaseUrl() != null && !config.getEmBaseUrl().trim().isEmpty()) {
                baseUrl = config.getEmBaseUrl().trim();
            }
            if (config.getEmApiKey() != null && !config.getEmApiKey().trim().isEmpty()) {
                apiKey = config.getEmApiKey().trim();
            }
            if (config.getEmModel() != null && !config.getEmModel().trim().isEmpty()) {
                currentModel = config.getEmModel().trim();
            }
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Embedding API Key 未配置！");
        }

        logger.info("初始化 Embedding 服务: baseUrl={}, model={}", baseUrl, currentModel);

        // --- B. 核心修复：配置 HTTP 工厂 (解决连接中止问题) ---
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30_000);
        requestFactory.setReadTimeout(180_000);

        // --- C. 配置 JSON 转换器 ---
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.getFactory().configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, false);

        // --- D. 构建 RestClient ---
        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }

    /**
     * 生成文本向量
     */
    public List<Double> generateEmbedding(String text) {
        try {
            if (text == null || text.isEmpty()) {
                logger.warn("生成embedding失败: 文本为空");
                return null;
            }

            logger.debug("开始生成embedding, 文本长度: {}, model: {}", text.length(), currentModel);

            EmbeddingRequest request = new EmbeddingRequest(currentModel, text);

            EmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                logger.error("生成embedding失败: response为空");
                return null;
            }

            List<Double> embedding = response.getData().get(0).getEmbedding();
            logger.debug("生成embedding成功, 向量维度: {}", embedding != null ? embedding.size() : 0);
            return embedding;

        } catch (Exception e) {
            logger.error("生成embedding失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 动态刷新配置
     */
    public void refreshConfig() {
        this.restClient = buildRestClient();
    }

    /**
     * 获取当前使用的模型
     */
    public String getCurrentModel() {
        return currentModel;
    }

    // ===== 内部类 =====

    public static class EmbeddingRequest {
        private String model;
        private String input;

        public EmbeddingRequest(String model, String input) {
            this.model = model;
            this.input = input;
        }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
    }

    public static class EmbeddingResponse {
        private List<EmbeddingData> data;

        public List<EmbeddingData> getData() { return data; }
        public void setData(List<EmbeddingData> data) { this.data = data; }
    }

    public static class EmbeddingData {
        private List<Double> embedding;

        public List<Double> getEmbedding() { return embedding; }
        public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
    }
}