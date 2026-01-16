package com.cyberstrike.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * MCP 协议类型定义
 */
public class McpTypes {

    public static final String PROTOCOL_VERSION = "2024-11-05";

    /**
     * MCP 消息 (JSON-RPC 2.0)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("jsonrpc")
        private String version = "2.0";

        @JsonProperty("id")
        private Object id;

        @JsonProperty("method")
        private String method;

        @JsonProperty("params")
        private Object params;

        @JsonProperty("result")
        private Object result;

        @JsonProperty("error")
        private McpError error;

        // Getters and Setters
        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Object getParams() {
            return params;
        }

        public void setParams(Object params) {
            this.params = params;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public McpError getError() {
            return error;
        }

        public void setError(McpError error) {
            this.error = error;
        }
    }

    /**
     * MCP 错误
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpError {
        private int code;
        private String message;
        private Object data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    /**
     * 初始化请求参数
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InitializeRequest {
        private String protocolVersion = PROTOCOL_VERSION;
        private Map<String, Object> capabilities = Map.of();
        private ClientInfo clientInfo = new ClientInfo("CyberStrikeAI", "1.0.0");

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public void setProtocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        public Map<String, Object> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(Map<String, Object> capabilities) {
            this.capabilities = capabilities;
        }

        public ClientInfo getClientInfo() {
            return clientInfo;
        }

        public void setClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
        }
    }

    /**
     * 客户端信息
     */
    public static class ClientInfo {
        private String name;
        private String version;

        public ClientInfo() {
        }

        public ClientInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    /**
     * 初始化响应
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitializeResponse {
        private String protocolVersion;
        private ServerCapabilities capabilities;
        private ServerInfo serverInfo;

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public void setProtocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        public ServerCapabilities getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(ServerCapabilities capabilities) {
            this.capabilities = capabilities;
        }

        public ServerInfo getServerInfo() {
            return serverInfo;
        }

        public void setServerInfo(ServerInfo serverInfo) {
            this.serverInfo = serverInfo;
        }
    }

    /**
     * 服务器能力
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerCapabilities {
        private Map<String, Object> tools;
        private Map<String, Object> prompts;
        private Map<String, Object> resources;

        public Map<String, Object> getTools() {
            return tools;
        }

        public void setTools(Map<String, Object> tools) {
            this.tools = tools;
        }

        public Map<String, Object> getPrompts() {
            return prompts;
        }

        public void setPrompts(Map<String, Object> prompts) {
            this.prompts = prompts;
        }

        public Map<String, Object> getResources() {
            return resources;
        }

        public void setResources(Map<String, Object> resources) {
            this.resources = resources;
        }
    }

    /**
     * 服务器信息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerInfo {
        private String name;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    /**
     * MCP 工具定义
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;

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

        public Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
        }
    }

    /**
     * 列出工具响应
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListToolsResponse {
        private List<Tool> tools;

        public List<Tool> getTools() {
            return tools;
        }

        public void setTools(List<Tool> tools) {
            this.tools = tools;
        }
    }

    /**
     * 调用工具请求
     */
    public static class CallToolRequest {
        private String name;
        private Map<String, Object> arguments;

        public CallToolRequest() {
        }

        public CallToolRequest(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public void setArguments(Map<String, Object> arguments) {
            this.arguments = arguments;
        }
    }

    /**
     * 调用工具响应
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallToolResponse {
        private List<Content> content;
        private boolean isError;

        public List<Content> getContent() {
            return content;
        }

        public void setContent(List<Content> content) {
            this.content = content;
        }

        public boolean isIsError() {
            return isError;
        }

        public void setIsError(boolean isError) {
            this.isError = isError;
        }
    }

    /**
     * 内容
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private String type;
        private String text;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    /**
     * 工具执行结果
     */
    public static class ToolResult {
        private List<Content> content;
        private boolean isError;

        public ToolResult() {
        }

        public ToolResult(List<Content> content, boolean isError) {
            this.content = content;
            this.isError = isError;
        }

        public List<Content> getContent() {
            return content;
        }

        public void setContent(List<Content> content) {
            this.content = content;
        }

        public boolean isIsError() {
            return isError;
        }

        public void setIsError(boolean isError) {
            this.isError = isError;
        }
    }
}
