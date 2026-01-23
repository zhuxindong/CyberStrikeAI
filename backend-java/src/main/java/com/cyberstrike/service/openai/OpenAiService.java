package com.cyberstrike.service.openai;

import com.cyberstrike.entity.Config;
import com.cyberstrike.repository.ConfigRepository;
import com.cyberstrike.service.openai.model.OpenAIModels;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionRequest;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);


    private final RestClient.Builder restClientBuilder;
    private final ConfigRepository configRepository;

    public OpenAiService(ConfigRepository configRepository,
               RestClient.Builder builder) {
        this.configRepository = configRepository;
        this.restClientBuilder = builder;
        // 注意：这里不再构建 this.restClient
    }
    private RestClient createRestClient() {
        // 从数据库获取最新配置
        Config config = configRepository.findById(1L).orElse(null);

        String baseUrl = "https://api.openai.com"; // 默认值
        String apiKey = System.getenv("OPENAI_API_KEY"); // 尝试从环境变量获取默认值

        if (config != null) {
            if (config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()) {
                baseUrl = config.getBaseUrl().trim();
            }
            if (config.getApiKey() != null && !config.getApiKey().trim().isEmpty()) {
                apiKey = config.getApiKey().trim();
            }
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API Key 未配置！");
        }
        return restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

//    public OpenAiService(ConfigRepository configRepository,
//            RestClient.Builder builder) {
//        // 1. 从数据库加载配置 (假设 ID = 1 是 OpenAI 的配置)
//        Config config = configRepository.findById(1L).orElse(null);
//
//        // 2. 设置默认值以防数据库中没有数据
//        String baseUrl = ""; // 默认值
//        String apiKey = "r"; // 默认值，或者抛出异常
//
//        if (config != null) {
//            // 如果数据库中有值，则覆盖默认值
//            if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
//                baseUrl = config.getBaseUrl();
//            }
//            if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
//                apiKey = config.getApiKey();
//            }
//        } else {
//            log.warn("未在数据库中找到 ID 为 1 的配置，将使用默认值或环境变量。");
//        }
//
//        // Ensure base URL ends with / if needed, but OpenAI usually wants
//        // https://api.openai.com
//        // We will append /v1/chat/completions manually
//
//        this.restClient = builder
//                .baseUrl(baseUrl)
//                .defaultHeader("Authorization", "Bearer " + apiKey)
//                .defaultHeader("Content-Type", "application/json")
//                .build();
//    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        return createRestClient().post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
    }

    // Streaming implementation would return Flux or similar, but for SSE we might
    // handle it differently.
    // For now, focus on non-stream or use simple input stream reading.
    // Spring RestClient supports exchange() which gives access to connection.

    public OpenAIModels.EmbeddingResponse createEmbeddings(OpenAIModels.EmbeddingRequest request) {
        return createRestClient().post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenAIModels.EmbeddingResponse.class);
    }
}
