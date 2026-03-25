package com.cyberstrike.service;

import com.cyberstrike.dto.WebshellConversationItem;
import com.cyberstrike.dto.WebshellExecRequest;
import com.cyberstrike.dto.WebshellExecResponse;
import com.cyberstrike.dto.WebshellFileOpRequest;
import com.cyberstrike.dto.WebshellFileOpResponse;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.WebshellConnection;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.WebshellConnectionRepository;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 【Go -> Java 迁移新增】WebShell 服务
 * 
 * 对齐 Go 实现：cyberstrike-ai/internal/handler/webshell.go
 * 代理执行 WebShell 命令（类似冰蝎/蚁剑），避免前端跨域并统一构建请求
 */
@Service
public class WebshellService {

    private static final Logger logger = LoggerFactory.getLogger(WebshellService.class);
    private static final String DEFAULT_CMD_PARAM = "cmd";
    private static final String DEFAULT_TYPE = "php";
    private static final String DEFAULT_METHOD = "post";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; CyberStrikeAI-WebShell/1.0)";

    private final WebshellConnectionRepository connectionRepository;
    private final ConversationRepository conversationRepository;
    private final CloseableHttpClient httpClient;

    public WebshellService(WebshellConnectionRepository connectionRepository,
                           ConversationRepository conversationRepository) {
        this.connectionRepository = connectionRepository;
        this.conversationRepository = conversationRepository;
        this.httpClient = HttpClients.custom()
//                .setMaxTotalConnections(20)
                .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setConnectTimeout(Timeout.of(Duration.ofSeconds(30)))
                        .setResponseTimeout(Timeout.of(Duration.ofSeconds(30)))
                        .build())
                .build();
    }

    /**
     * 执行命令（前端传入连接信息 + 命令）
     */
    public WebshellExecResponse exec(WebshellExecRequest request) {
        String url = trim(request.getUrl());
        String command = trim(request.getCommand());
        
        if (url == null || url.isEmpty() || command == null || command.isEmpty()) {
            return WebshellExecResponse.error("url and command are required");
        }

        // 验证 URL
        if (!isValidHttpUrl(url)) {
            return WebshellExecResponse.error("invalid url: only http(s) allowed");
        }

        String shellType = getShellType(request.getType());
        boolean useGet = "get".equalsIgnoreCase(trim(request.getMethod()));
        String cmdParam = getCmdParam(request.getCmdParam());
        String password = trim(request.getPassword());

        try {
            String execUrl = buildExecUrl(url, shellType, password, cmdParam, command);
            HttpUriRequestBase httpRequest;
            
            if (useGet) {
                httpRequest = new HttpGet(execUrl);
            } else {
                HttpPost post = new HttpPost(url);
                String body = buildExecBody(shellType, password, cmdParam, command);
                post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
                httpRequest = post;
            }
            
            httpRequest.setHeader("User-Agent", USER_AGENT);

            try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                String output = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                int statusCode = response.getCode();
                return WebshellExecResponse.success(output, statusCode);
            }
        } catch (IOException | ParseException e) {
            logger.warn("WebShell exec error: {}", e.getMessage());
            return WebshellExecResponse.error(e.getMessage());
        }
    }

    /**
     * 在指定连接上执行命令（供 MCP/Agent 等非 HTTP 调用）
     */
    public WebshellExecResponse execWithConnection(String connectionId, String command) {
        Optional<WebshellConnection> optConn = connectionRepository.findById(connectionId);
        if (optConn.isEmpty()) {
            return WebshellExecResponse.error("connection not found");
        }

        WebshellConnection conn = optConn.get();
        command = trim(command);
        if (command == null || command.isEmpty()) {
            return WebshellExecResponse.error("command is required");
        }

        boolean useGet = "get".equalsIgnoreCase(trim(conn.getMethod()));
        String cmdParam = getCmdParam(conn.getCmdParam());
        String password = trim(conn.getPassword());
        String shellType = getShellType(conn.getType());

        try {
            HttpUriRequestBase httpRequest;
            if (useGet) {
                String execUrl = buildExecUrl(conn.getUrl(), shellType, password, cmdParam, command);
                httpRequest = new HttpGet(execUrl);
            } else {
                HttpPost post = new HttpPost(conn.getUrl());
                String body = buildExecBody(shellType, password, cmdParam, command);
                post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
                httpRequest = post;
            }
            
            httpRequest.setHeader("User-Agent", USER_AGENT);

            try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                String output = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                boolean ok = response.getCode() == 200;
                return new WebshellExecResponse(ok, output, null, response.getCode());
            }
        } catch (IOException | ParseException e) {
            logger.warn("WebShell exec with connection error: {}", e.getMessage());
            return WebshellExecResponse.error(e.getMessage());
        }
    }

    /**
     * 文件操作
     */
    public WebshellFileOpResponse fileOp(WebshellFileOpRequest request) {
        String url = trim(request.getUrl());
        String action = request.getAction() == null ? "" : request.getAction().toLowerCase().trim();
        
        if (url == null || url.isEmpty() || action.isEmpty()) {
            return WebshellFileOpResponse.error("url and action are required");
        }

        // 验证 URL
        if (!isValidHttpUrl(url)) {
            return WebshellFileOpResponse.error("invalid url: only http(s) allowed");
        }

        String shellType = getShellType(request.getType());
        boolean useGet = "get".equalsIgnoreCase(trim(request.getMethod()));
        String cmdParam = getCmdParam(request.getCmdParam());
        String password = trim(request.getPassword());

        // 构建命令
        String command = buildFileCommand(action, shellType, request);
        if (command == null) {
            return WebshellFileOpResponse.error("unsupported action: " + action);
        }

        try {
            HttpUriRequestBase httpRequest;
            if (useGet) {
                String execUrl = buildExecUrl(url, shellType, password, cmdParam, command);
                httpRequest = new HttpGet(execUrl);
            } else {
                HttpPost post = new HttpPost(url);
                String body = buildExecBody(shellType, password, cmdParam, command);
                post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
                httpRequest = post;
            }
            
            httpRequest.setHeader("User-Agent", USER_AGENT);

            try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                String output = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                boolean ok = response.getCode() == 200;
                if (ok) {
                    return WebshellFileOpResponse.success(output);
                } else {
                    return WebshellFileOpResponse.error("HTTP " + response.getCode());
                }
            }
        } catch (IOException | ParseException e) {
            logger.warn("WebShell fileOp error: {}", e.getMessage());
            return WebshellFileOpResponse.error(e.getMessage());
        }
    }

    /**
     * 在指定连接上执行文件操作（供 MCP/Agent 调用）
     */
    public WebshellFileOpResponse fileOpWithConnection(String connectionId, String action, String path, String content, String targetPath) {
        Optional<WebshellConnection> optConn = connectionRepository.findById(connectionId);
        if (optConn.isEmpty()) {
            return WebshellFileOpResponse.error("connection not found");
        }

        WebshellConnection conn = optConn.get();
        action = action == null ? "" : action.toLowerCase().trim();
        String shellType = getShellType(conn.getType());
        
        // 构建请求对象
        WebshellFileOpRequest request = new WebshellFileOpRequest();
        request.setPath(path);
        request.setContent(content);
        request.setTargetPath(targetPath);
        
        String command = buildFileCommand(action, shellType, request);
        if (command == null) {
            return WebshellFileOpResponse.error("unsupported action: " + action + " (supported: list, read, write)");
        }

        boolean useGet = "get".equalsIgnoreCase(trim(conn.getMethod()));
        String cmdParam = getCmdParam(conn.getCmdParam());
        String password = trim(conn.getPassword());

        try {
            HttpUriRequestBase httpRequest;
            if (useGet) {
                String execUrl = buildExecUrl(conn.getUrl(), shellType, password, cmdParam, command);
                httpRequest = new HttpGet(execUrl);
            } else {
                HttpPost post = new HttpPost(conn.getUrl());
                String body = buildExecBody(shellType, password, cmdParam, command);
                post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
                httpRequest = post;
            }
            
            httpRequest.setHeader("User-Agent", USER_AGENT);

            try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                String output = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                boolean ok = response.getCode() == 200;
                if (ok) {
                    return WebshellFileOpResponse.success(output);
                } else {
                    return WebshellFileOpResponse.error("HTTP " + response.getCode());
                }
            }
        } catch (IOException | ParseException e) {
            logger.warn("WebShell fileOp with connection error: {}", e.getMessage());
            return WebshellFileOpResponse.error(e.getMessage());
        }
    }

    // ==================== AI History 相关 ====================

    /**
     * 获取指定 WebShell 连接的 AI 助手对话历史
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go GetAIHistory
     */
    public Map<String, Object> getAIHistory(String connectionId) {
        if (connectionId == null || connectionId.trim().isEmpty()) {
            return Map.of("conversationId", null, "messages", java.util.List.of());
        }

        try {
            Optional<Conversation> convOpt = conversationRepository
                    .findTopByWebshellConnectionIdOrderByUpdatedAtDesc(connectionId);
            
            if (convOpt.isEmpty()) {
                return Map.of("conversationId", null, "messages", java.util.List.of());
            }

            Conversation conv = convOpt.get();
//            return Map.of(
//                "conversationId", conv.getId(),
//                "messages", conv.getMessages() != null ? conv.getMessages() : java.util.List.of()
//            );
            return null;
        } catch (Exception e) {
            logger.warn("获取 WebShell AI 对话失败: {}", e.getMessage());
            return Map.of("conversationId", null, "messages", java.util.List.of());
        }
    }

    /**
     * 列出该 WebShell 连接下的所有 AI 对话（供侧边栏）
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go ListAIConversations
     */
    public List<WebshellConversationItem> listAIConversations(String connectionId) {
        if (connectionId == null || connectionId.trim().isEmpty()) {
            return java.util.List.of();
        }

        try {
            List<Conversation> conversations = conversationRepository
                    .findByWebshellConnectionIdOrderByUpdatedAtDesc(connectionId);
            
            if (conversations == null || conversations.isEmpty()) {
                return java.util.List.of();
            }

            return conversations.stream()
                    .map(conv -> new WebshellConversationItem(
                        conv.getId(),
                        conv.getTitle(),
                        conv.getUpdatedAt()
                    ))
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.warn("列出 WebShell AI 对话失败: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    // ==================== 私有辅助方法 ====================

    private String getShellType(String type) {
        String shellType = trim(type);
        if (shellType == null || shellType.isEmpty()) {
            return DEFAULT_TYPE;
        }
        return shellType.toLowerCase();
    }

    private String getCmdParam(String cmdParam) {
        String param = trim(cmdParam);
        if (param == null || param.isEmpty()) {
            return DEFAULT_CMD_PARAM;
        }
        return param;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private boolean isValidHttpUrl(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://");
    }

    /**
     * 构建 GET 请求的完整 URL
     */
    private String buildExecUrl(String baseUrl, String shellType, String password, String cmdParam, String command) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!baseUrl.contains("?")) {
            sb.append("?");
        } else {
            sb.append("&");
        }
        
        try {
            sb.append("pass=").append(URLEncoder.encode(password == null ? "" : password, StandardCharsets.UTF_8));
            sb.append("&");
            sb.append(cmdParam).append("=").append(URLEncoder.encode(command, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // fallback to simple concatenation
            sb.append("pass=").append(password == null ? "" : password);
            sb.append("&").append(cmdParam).append("=").append(command);
        }
        
        return sb.toString();
    }

    /**
     * 构建 POST 请求体
     */
    private String buildExecBody(String shellType, String password, String cmdParam, String command) {
        StringBuilder sb = new StringBuilder();
        sb.append("pass=").append(urlEncode(password == null ? "" : password));
        sb.append("&");
        sb.append(cmdParam).append("=").append(urlEncode(command));
        return sb.toString();
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 构建文件操作命令
     */
    private String buildFileCommand(String action, String shellType, WebshellFileOpRequest request) {
        String path = trim(request.getPath());
        String content = trim(request.getContent());
        String targetPath = trim(request.getTargetPath());
        
        boolean isAsp = "asp".equalsIgnoreCase(shellType) || "aspx".equalsIgnoreCase(shellType);

        switch (action) {
            case "list":
                String listPath = path == null || path.isEmpty() ? "." : path;
                if (isAsp) {
                    return "dir " + escapePath(listPath);
                } else {
                    return "ls -la " + escapePath(listPath);
                }
                
            case "read":
                if (path == null || path.isEmpty()) {
                    return null; // path is required
                }
                if (isAsp) {
                    return "type " + escapePath(path);
                } else {
                    return "cat " + escapePath(path);
                }
                
            case "delete":
                if (path == null || path.isEmpty()) {
                    return null; // path is required
                }
                if (isAsp) {
                    return "del " + escapePath(path);
                } else {
                    return "rm -f " + escapePath(path);
                }
                
            case "write":
                if (path == null || path.isEmpty()) {
                    return null; // path is required
                }
                return "echo " + escapeForEcho(content) + " > " + escapePath(path);
                
            case "mkdir":
                if (path == null || path.isEmpty()) {
                    return null; // path is required for mkdir
                }
                if (isAsp) {
                    return "md " + escapePath(path);
                } else {
                    return "mkdir -p " + escapePath(path);
                }
                
            case "rename":
                if (path == null || path.isEmpty() || targetPath == null || targetPath.isEmpty()) {
                    return null; // path and target_path are required
                }
                if (isAsp) {
                    return "move /y " + escapePath(path) + " " + escapePath(targetPath);
                } else {
                    return "mv " + escapePath(path) + " " + escapePath(targetPath);
                }
                
            case "upload":
                if (path == null || path.isEmpty()) {
                    return null; // path is required
                }
                if (content != null && content.length() > 512 * 1024) {
                    return null; // too large
                }
                return "echo " + "'" + content + "'" + " | base64 -d > " + escapePath(path);
                
            case "upload_chunk":
                if (path == null || path.isEmpty()) {
                    return null; // path is required
                }
                int chunkIndex = request.getChunkIndex() != null ? request.getChunkIndex() : 0;
                String redir = chunkIndex == 0 ? ">" : ">>";
                return "echo " + "'" + content + "'" + " | base64 -d " + redir + " " + escapePath(path);
                
            default:
                return null;
        }
    }

    /**
     * 转义路径（简单转义单引号）
     */
    private String escapePath(String p) {
        if (p == null || p.isEmpty()) {
            return ".";
        }
        return "'" + p.replace("'", "'\\''") + "'";
    }

    /**
     * 转义 echo 内容
     */
    private String escapeForEcho(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
