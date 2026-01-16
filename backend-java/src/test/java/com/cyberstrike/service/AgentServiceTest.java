package com.cyberstrike.service;

import com.cyberstrike.dto.ChatRequest;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.MessageRepository;
import com.cyberstrike.service.openai.OpenAiService;
import com.cyberstrike.service.openai.model.OpenAIModels;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionChoice;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionMessage;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionResponse;
import com.cyberstrike.service.openai.model.OpenAIModels.FunctionCall;
import com.cyberstrike.service.openai.model.OpenAIModels.ToolCall;
import com.cyberstrike.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private OpenAiService openAiService;

    @Mock
    private com.cyberstrike.tool.YamlToolLoader yamlToolLoader;
    @Mock
    private com.cyberstrike.service.KnowledgeService knowledgeService;
    @Mock
    private com.cyberstrike.service.PythonVenvService pythonVenvService;

    private ToolRegistry toolRegistry;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock YamlToolLoader to return empty list or mocked tools if needed
        when(yamlToolLoader.getAllTools()).thenReturn(Collections.emptyList());
        when(yamlToolLoader.getEnabledTools()).thenReturn(Collections.emptyList());

        toolRegistry = new ToolRegistry(yamlToolLoader, knowledgeService, pythonVenvService);

        // Correct order: OpenAiService, ConversationRepository, MessageRepository,
        // ToolRegistry, String modelName
        agentService = new AgentService(openAiService, conversationRepository, messageRepository, toolRegistry,
                "gpt-3.5-turbo");
    }

    @Test
    void testAgentLoop_ToolExecution() throws InterruptedException, IOException {
        // Mock OpenAI Response 1: Call Tool "nmap_scan"
        ChatCompletionResponse response1 = new ChatCompletionResponse();
        ChatCompletionChoice choice1 = new ChatCompletionChoice();
        choice1.setFinishReason("tool_calls");

        ChatCompletionMessage msg1 = new ChatCompletionMessage();
        msg1.setRole("assistant");
        msg1.setContent(null);

        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_123");
        toolCall.setType("function");
        FunctionCall fn = new FunctionCall();
        fn.setName("nmap_scan");
        fn.setArguments("{\"target\": \"127.0.0.1\", \"options\": \"-p 80\"}");
        toolCall.setFunction(fn);

        msg1.setToolCalls(Collections.singletonList(toolCall));
        choice1.setMessage(msg1);
        response1.setChoices(Collections.singletonList(choice1));

        // Mock OpenAI Response 2: Final Answer
        ChatCompletionResponse response2 = new ChatCompletionResponse();
        ChatCompletionChoice choice2 = new ChatCompletionChoice();
        choice2.setFinishReason("stop");
        ChatCompletionMessage msg2 = new ChatCompletionMessage();
        msg2.setRole("assistant");
        msg2.setContent("Scan complete. Port 80 is open.");
        choice2.setMessage(msg2);
        response2.setChoices(Collections.singletonList(choice2));

        when(openAiService.chatCompletion(any(OpenAIModels.ChatCompletionRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        // Mock DB calls
        when(conversationRepository.save(any())).thenAnswer(i -> {
            var c = (com.cyberstrike.entity.Conversation) i.getArgument(0);
            c.setId("conv_TEST");
            return c;
        });

        ChatRequest request = new ChatRequest();
        request.setMessage("Scan localhost");

        SseEmitter emitter = agentService.agentLoopStream(request);

        // Wait a bit for async execution
        Thread.sleep(2000);

        // Verify OpenAI called twice
        verify(openAiService, times(2)).chatCompletion(any());

        // Verify Tool Execution logic implicitly by the fact that second call happened
        // with tool result
        // We can capture arguments to verify the second call contained tool result
        // ArgumentCaptor<ChatCompletionRequest> captor =
        // ArgumentCaptor.forClass(ChatCompletionRequest.class);
        // verify(openAiService, atLeast(2)).chatCompletion(captor.capture());
        // ... verify captor.getAllValues().get(1).getMessages() contains tool roles
    }
}
