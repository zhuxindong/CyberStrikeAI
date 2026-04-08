package com.cyberstrike.controller;

import com.cyberstrike.dto.WebshellConversationItem;
import com.cyberstrike.dto.WebshellExecRequest;
import com.cyberstrike.dto.WebshellExecResponse;
import com.cyberstrike.dto.WebshellFileOpRequest;
import com.cyberstrike.dto.WebshellFileOpResponse;
import com.cyberstrike.entity.WebshellConnection;
import com.cyberstrike.repository.WebshellConnectionRepository;
import com.cyberstrike.service.WebshellService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 【Go -> Java 迁移新增】WebShell 连接管理接口。
 *
 * 对齐 Go 路由（cyberstrike-ai/internal/app/app.go）：
 * - GET    /api/webshell/connections
 * - POST   /api/webshell/connections
 * - PUT    /api/webshell/connections/{id}
 * - DELETE /api/webshell/connections/{id}
 * - POST   /api/webshell/exec
 * - POST   /api/webshell/fileop
 *
 * 支持远程命令执行与文件操作（类似冰蝎/蚁剑）。
 */
@RestController
@RequestMapping("/api/webshell")
@Tag(name = "Webshell", description = "WebShell 连接、命令执行与文件操作接口")
public class WebshellController {

    private final WebshellConnectionRepository repository;
    private final WebshellService webshellService;

    public WebshellController(WebshellConnectionRepository repository, WebshellService webshellService) {
        this.repository = repository;
        this.webshellService = webshellService;
    }

    @Operation(summary = "列出 WebShell 连接", description = "GET /api/webshell/connections")
    @GetMapping("/connections")
    public ResponseEntity<?> listConnections() {
        List<WebshellConnection> list = repository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "创建 WebShell 连接", description = "POST /api/webshell/connections")
    @PostMapping("/connections")
    public ResponseEntity<?> createConnection(@RequestBody Map<String, Object> body) {
        String url = body.get("url") == null ? "" : String.valueOf(body.get("url")).trim();
        if (url.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }

        WebshellConnection conn = new WebshellConnection();
        // 对齐 Go：ws_ + 随机短 ID
        conn.setId("ws_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        conn.setUrl(url);
        conn.setPassword(body.get("password") == null ? "" : String.valueOf(body.get("password")).trim());
        conn.setType(body.get("type") == null ? "php" : String.valueOf(body.get("type")).trim().toLowerCase());
        conn.setMethod(body.get("method") == null ? "post" : String.valueOf(body.get("method")).trim().toLowerCase());
        conn.setCmdParam(body.get("cmd_param") == null ? "" : String.valueOf(body.get("cmd_param")).trim());
        conn.setRemark(body.get("remark") == null ? "" : String.valueOf(body.get("remark")).trim());
        conn.setCreatedAt(LocalDateTime.now());

        repository.save(conn);
        return ResponseEntity.created(URI.create("/api/webshell/connections/" + conn.getId())).body(conn);
    }

    @Operation(summary = "更新 WebShell 连接", description = "PUT /api/webshell/connections/{id}")
    @PutMapping("/connections/{id}")
    public ResponseEntity<?> updateConnection(@Parameter(description = "连接ID") @PathVariable String id, @RequestBody Map<String, Object> body) {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        Optional<WebshellConnection> optional = repository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "connection not found"));
        }

        WebshellConnection conn = optional.get();

        if (body.containsKey("url")) {
            String url = body.get("url") == null ? "" : String.valueOf(body.get("url")).trim();
            if (url.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
            }
            conn.setUrl(url);
        }
        if (body.containsKey("password")) {
            conn.setPassword(body.get("password") == null ? "" : String.valueOf(body.get("password")).trim());
        }
        if (body.containsKey("type")) {
            conn.setType(body.get("type") == null ? "php" : String.valueOf(body.get("type")).trim().toLowerCase());
        }
        if (body.containsKey("method")) {
            conn.setMethod(body.get("method") == null ? "post" : String.valueOf(body.get("method")).trim().toLowerCase());
        }
        if (body.containsKey("cmd_param")) {
            conn.setCmdParam(body.get("cmd_param") == null ? "" : String.valueOf(body.get("cmd_param")).trim());
        }
        if (body.containsKey("remark")) {
            conn.setRemark(body.get("remark") == null ? "" : String.valueOf(body.get("remark")).trim());
        }

        repository.save(conn);
        return ResponseEntity.ok(conn);
    }

    @Operation(summary = "删除 WebShell 连接", description = "DELETE /api/webshell/connections/{id}")
    @DeleteMapping("/connections/{id}")
    public ResponseEntity<?> deleteConnection(@Parameter(description = "连接ID") @PathVariable String id) {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        if (!repository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "connection not found"));
        }

        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ==================== 命令执行与文件操作 ====================

    /**
     * 执行命令（POST /api/webshell/exec）
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go Exec
     */
    @Operation(summary = "执行命令", description = "POST /api/webshell/exec")
    @PostMapping("/exec")
    public ResponseEntity<?> exec(@Valid @RequestBody WebshellExecRequest request) {
        WebshellExecResponse response = webshellService.exec(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 文件操作（POST /api/webshell/fileop）
     * 支持：list, read, delete, write, mkdir, rename, upload, upload_chunk
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go FileOp
     */
    @Operation(summary = "文件操作", description = "POST /api/webshell/fileop 支持 list/read/delete/write/mkdir/rename/upload 等")
    @PostMapping("/fileop")
    public ResponseEntity<?> fileOp(@Valid @RequestBody WebshellFileOpRequest request) {
        WebshellFileOpResponse response = webshellService.fileOp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 使用已保存的连接执行命令（POST /api/webshell/connections/{id}/exec）
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go ExecWithConnection
     */
    @Operation(summary = "使用已保存连接执行命令", description = "POST /api/webshell/connections/{id}/exec")
    @PostMapping("/connections/{id}/exec")
    public ResponseEntity<?> execWithConnection(@Parameter(description = "连接ID") @PathVariable String id, @RequestBody Map<String, Object> body) {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        String command = body.get("command") == null ? "" : String.valueOf(body.get("command")).trim();
        if (command.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "command is required"));
        }

        WebshellExecResponse response = webshellService.execWithConnection(id, command);
        return ResponseEntity.ok(response);
    }

    /**
     * 使用已保存的连接执行文件操作（POST /api/webshell/connections/{id}/fileop）
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go FileOpWithConnection
     */
    @Operation(summary = "使用已保存连接进行文件操作", description = "POST /api/webshell/connections/{id}/fileop")
    @PostMapping("/connections/{id}/fileop")
    public ResponseEntity<?> fileOpWithConnection(@Parameter(description = "连接ID") @PathVariable String id, @RequestBody Map<String, Object> body) {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        String action = body.get("action") == null ? "" : String.valueOf(body.get("action")).trim();
        if (action.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "action is required"));
        }

        String path = body.get("path") == null ? "" : String.valueOf(body.get("path")).trim();
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content")).trim();
        String targetPath = body.get("target_path") == null ? "" : String.valueOf(body.get("target_path")).trim();

        WebshellFileOpResponse response = webshellService.fileOpWithConnection(id, action, path, content, targetPath);
        return ResponseEntity.ok(response);
    }

    // ==================== AI History 相关 ====================

    /**
     * 获取指定 WebShell 连接的 AI 助手对话历史（GET /api/webshell/connections/{id}/ai-history）
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go GetAIHistory
     */
    @Operation(summary = "获取连接的 AI 助手历史", description = "GET /api/webshell/connections/{id}/ai-history")
    @GetMapping("/connections/{id}/ai-history")
    public ResponseEntity<?> getAIHistory(@Parameter(description = "连接ID") @PathVariable String id) {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        Map<String, Object> history = webshellService.getAIHistory(id);
        return ResponseEntity.ok(history);
    }

    /**
     * 列出该 WebShell 连接下的所有 AI 对话（GET /api/webshell/connections/{id}/conversations）
     * 对齐 Go：cyberstrike-ai/internal/handler/webshell.go ListAIConversations
     */
    @Operation(summary = "列出连接下的 AI 对话", description = "GET /api/webshell/connections/{id}/conversations")
    @GetMapping("/connections/{id}/conversations")
    public ResponseEntity<?> listAIConversations(@Parameter(description = "连接ID") @PathVariable String id) {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        List<WebshellConversationItem> list = webshellService.listAIConversations(id);
        if (list == null) {
            list = new ArrayList<>();
        }
        return ResponseEntity.ok(list);
    }
}
