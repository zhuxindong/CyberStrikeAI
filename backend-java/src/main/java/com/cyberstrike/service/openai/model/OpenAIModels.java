package com.cyberstrike.service.openai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class OpenAIModels {

    // --- Request ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionRequest {
        @JsonProperty("model")
        private String model;
        @JsonProperty("messages")
        private List<ChatCompletionMessage> messages;
        @JsonProperty("tools")
        private List<Tool> tools;
        @JsonProperty("tool_choice")
        private Object toolChoice;
        @JsonProperty("temperature")
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        @JsonProperty("stream")
        private Boolean stream;

        public ChatCompletionRequest() {
        }

        public ChatCompletionRequest(String model, List<ChatCompletionMessage> messages, List<Tool> tools,
                Object toolChoice, Double temperature, Integer maxTokens, Boolean stream) {
            this.model = model;
            this.messages = messages;
            this.tools = tools;
            this.toolChoice = toolChoice;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.stream = stream;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private List<ChatCompletionMessage> messages;
            private List<Tool> tools;
            private Object toolChoice;
            private Double temperature;
            private Integer maxTokens;
            private Boolean stream;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder messages(List<ChatCompletionMessage> messages) {
                this.messages = messages;
                return this;
            }

            public Builder tools(List<Tool> tools) {
                this.tools = tools;
                return this;
            }

            public Builder toolChoice(Object toolChoice) {
                this.toolChoice = toolChoice;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder stream(Boolean stream) {
                this.stream = stream;
                return this;
            }

            public ChatCompletionRequest build() {
                return new ChatCompletionRequest(model, messages, tools, toolChoice, temperature, maxTokens, stream);
            }
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<ChatCompletionMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<ChatCompletionMessage> messages) {
            this.messages = messages;
        }

        public List<Tool> getTools() {
            return tools;
        }

        public void setTools(List<Tool> tools) {
            this.tools = tools;
        }

        public Object getToolChoice() {
            return toolChoice;
        }

        public void setToolChoice(Object toolChoice) {
            this.toolChoice = toolChoice;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Boolean getStream() {
            return stream;
        }

        public void setStream(Boolean stream) {
            this.stream = stream;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionMessage {
        @JsonProperty("role")
        private String role = "user";
        @JsonProperty("content")
        private String content;
        @JsonProperty("name")
        private String name;
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;

        public ChatCompletionMessage() {
        }

        public ChatCompletionMessage(String role, String content, String name, List<ToolCall> toolCalls,
                String toolCallId) {
            this.role = role != null ? role : "user";
            this.content = content;
            this.name = name;
            this.toolCalls = toolCalls;
            this.toolCallId = toolCallId;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String role = "user";
            private String content;
            private String name;
            private List<ToolCall> toolCalls;
            private String toolCallId;

            public Builder role(String role) {
                this.role = role;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder toolCalls(List<ToolCall> toolCalls) {
                this.toolCalls = toolCalls;
                return this;
            }

            public Builder toolCallId(String toolCallId) {
                this.toolCallId = toolCallId;
                return this;
            }

            public ChatCompletionMessage build() {
                return new ChatCompletionMessage(role, content, name, toolCalls, toolCallId);
            }
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public void setToolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        @JsonProperty("type")
        private String type;
        @JsonProperty("function")
        private Function function;

        public Tool() {
        }

        public Tool(String type, Function function) {
            this.type = type;
            this.function = function;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Function getFunction() {
            return function;
        }

        public void setFunction(Function function) {
            this.function = function;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        @JsonProperty("name")
        private String name;
        @JsonProperty("description")
        private String description;
        @JsonProperty("parameters")
        private Object parameters;

        public Function() {
        }

        public Function(String name, String description, Object parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getParameters() {
            return parameters;
        }

        public void setParameters(Object parameters) {
            this.parameters = parameters;
        }

        // Helper for backwards compatibility if needed, but usually parameters is
        // Object
        public String getArguments() {
            return null;
        }
    }

    // --- Response ---

    public static class ChatCompletionResponse {
        @JsonProperty("id")
        private String id;
        @JsonProperty("object")
        private String object;
        @JsonProperty("created")
        private Long created;
        @JsonProperty("model")
        private String model;
        @JsonProperty("choices")
        private List<ChatCompletionChoice> choices;
        @JsonProperty("usage")
        private Usage usage;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public Long getCreated() {
            return created;
        }

        public void setCreated(Long created) {
            this.created = created;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<ChatCompletionChoice> getChoices() {
            return choices;
        }

        public void setChoices(List<ChatCompletionChoice> choices) {
            this.choices = choices;
        }

        public Usage getUsage() {
            return usage;
        }

        public void setUsage(Usage usage) {
            this.usage = usage;
        }
    }

    public static class ChatCompletionChoice {
        @JsonProperty("index")
        private Integer index;
        @JsonProperty("message")
        private ChatCompletionMessage message;
        @JsonProperty("delta")
        private ChatCompletionMessage delta;
        @JsonProperty("finish_reason")
        private String finishReason;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public ChatCompletionMessage getMessage() {
            return message;
        }

        public void setMessage(ChatCompletionMessage message) {
            this.message = message;
        }

        public ChatCompletionMessage getDelta() {
            return delta;
        }

        public void setDelta(ChatCompletionMessage delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        @JsonProperty("id")
        private String id;
        @JsonProperty("type")
        private String type;
        @JsonProperty("function")
        private FunctionCall function;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public FunctionCall getFunction() {
            return function;
        }

        public void setFunction(FunctionCall function) {
            this.function = function;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCall {
        @JsonProperty("name")
        private String name;
        @JsonProperty("arguments")
        private String arguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }

    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
