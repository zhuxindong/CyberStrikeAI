package com.cyberstrike.service.openai;

import com.cyberstrike.entity.Config;
import com.cyberstrike.repository.ConfigRepository;
import com.cyberstrike.service.openai.model.OpenAIModels;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class OpenAiService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiService.class);

    // --- 1. 核心修改：将 RestClient 定义为成员变量 (单例模式) ---
    private volatile  RestClient restClient;

    private final ConfigRepository configRepository;

    private final RestClient.Builder restClientBuilder;
    // --- 2. 注入 Builder ---
    @Autowired
    public OpenAiService(ConfigRepository configRepository, RestClient.Builder restClientBuilder) {
        this.configRepository = configRepository;
        this.restClientBuilder = restClientBuilder;
        this.restClient = buildRestClient(); // 首次启动时构建
    }

    private RestClient buildRestClient() {

        // --- A. 初始化配置 (只在启动时读取一次，提升性能) ---
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

        // --- B. 核心修复：配置 HTTP 工厂 (解决连接中止问题) ---
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        // 连接超时
        requestFactory.setConnectTimeout(30_000);

        // 读取超时 (关键：必须足够长，防止系统软件中止连接)
        // 强制转换为 int，防止编译错误
        requestFactory.setReadTimeout((int) 180_000);

        // --- C. 配置 JSON 转换器 ---
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.getFactory().configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, false);

        // --- D. 创建日志拦截器 ---
        ClientHttpRequestInterceptor loggingInterceptor = (request, body, execution) -> {
            // 可选：打印请求日志
            // System.out.println("Request: " + request.getMethod() + " " + request.getURI());
            return execution.execute(request, body);
        };

        // --- E. 构建 RestClient (只构建一次) ---
        return restClientBuilder
                .requestFactory(requestFactory) // 注入工厂
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .messageConverters(converters -> {
                    //converters.clear();
                    converters.add(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper));
                })
                .requestInterceptor(loggingInterceptor)
                .build();
    }

    // --- 3. 业务方法：直接使用单例的 restClient ---
    public OpenAIModels.ChatCompletionResponse chatCompletion(OpenAIModels.ChatCompletionRequest request) {
        ObjectMapper objectMapper = new ObjectMapper();
        // 先获取 JSON 字符串
        String s = restClient.post()
                .uri("/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        // 然后再手动反序列化为目标对象
        try {
            System.out.println("sssssssss:"+s);
            return objectMapper.readValue(s, OpenAIModels.ChatCompletionResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public OpenAIModels.EmbeddingResponse createEmbeddings(OpenAIModels.EmbeddingRequest request) {
        return restClient.post()
                .uri("/embeddings")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenAIModels.EmbeddingResponse.class);
    }

    /**
     * 动态刷新配置
     */
    public void refreshConfig() {
        // 重新构建 restClient
        this.restClient = buildRestClient();
    }
}