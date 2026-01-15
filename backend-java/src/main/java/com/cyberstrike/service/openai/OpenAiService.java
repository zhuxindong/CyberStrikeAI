package com.cyberstrike.service.openai;

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

    private final RestClient restClient;

    public OpenAiService(
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            RestClient.Builder builder) {

        // Ensure base URL ends with / if needed, but OpenAI usually wants
        // https://api.openai.com
        // We will append /v1/chat/completions manually

        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        return restClient.post()
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
}
