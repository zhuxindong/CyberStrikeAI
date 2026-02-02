package com.cyberstrike.service.openai;

import com.cyberstrike.entity.Config;
import com.cyberstrike.repository.ConfigRepository;
import com.cyberstrike.service.openai.model.OpenAIModels;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionRequest;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // --- 删除或注释掉这一行（它是导致 20015 错误的直接原因）---
        // objectMapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);

        // --- 保留：禁止控制字符（这是为了安全，防止注入）---
        objectMapper.getFactory().configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, false);

        // --- 优化：建议显式设置为不转义非 ASCII，虽然这是默认值 ---
        // 如果你使用的是较新版本的 Jackson，这行甚至不需要
        // objectMapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);

        // --- 其余配置保持不变 ---
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setObjectMapper(objectMapper);

        // --- 原有的配置逻辑 ---
        Config config = configRepository.findById(1L).orElse(null);
        String baseUrl = "https://api.openai.com";
        String apiKey = System.getenv("OPENAI_API_KEY");

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

        // 3. 构建 RestClient
        // 关键区别：使用 messageConverters() 方法注入转换器
        return restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                // --- 注入消息转换器 ---
                // 这会替换默认的转换器，确保使用我们配置的 ObjectMapper
                .messageConverters(converters -> {
                    converters.clear(); // 清除默认的
                    converters.add(jsonConverter); // 添加配置好的 JSON 转换器
                })
                // --- 添加这一行：注册拦截器 ---
                .requestInterceptor(loggingInterceptor)
                .build();
    }

    // 1. 创建日志拦截器
    ClientHttpRequestInterceptor loggingInterceptor = (request, body, execution) -> {
        // 打印请求头
        System.out.println("=== HTTP Request ===");
        System.out.println("URI: " + request.getURI());
        System.out.println("Method: " + request.getMethod());
        request.getHeaders().forEach((key, value) -> {
            System.out.println("Header: " + key + " = " + value);
        });

        // 打印请求体
        if (body.length > 0) {
            System.out.println("Body: " + new String(body, StandardCharsets.UTF_8));
        }

        // 执行请求
        return execution.execute(request, body);
    };

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
