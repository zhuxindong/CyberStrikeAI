package com.cyberstrike.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/chat-uploads")
@CrossOrigin(origins = "*")
@Tag(name = "ChatUploads", description = "对话附件（chat_uploads目录）管理接口")
public class ChatUploadsController {

    private static final String CHAT_UPLOADS_DIR = "chat_uploads";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_EDIT_BYTES = 2 * 1024 * 1024;
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Value("${cyberstrike.upload.path:./chat_uploads}")
    private String uploadBasePath;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatUploadsController.class);

    private Path getRootPath() {
        String basePath = uploadBasePath != null ? uploadBasePath : CHAT_UPLOADS_DIR;
        return Paths.get(basePath).toAbsolutePath().normalize();
    }

    private Path resolveUnderChatUploads(String relativePath) throws IOException {
        Path root = getRootPath();
        Path resolved;
        if (relativePath == null || relativePath.isEmpty()) {
            resolved = root;
        } else {
            String normalized = relativePath.replace("/", File.separator);
            resolved = root.resolve(normalized).normalize();
        }
        if (!resolved.startsWith(root)) {
            throw new IOException("path escapes chat_uploads root");
        }
        return resolved;
    }

    private Path findConversationDirectory(Path root, String conversationId) throws IOException {
        Path directPath = root.resolve(conversationId);
        if (Files.exists(directPath) && Files.isDirectory(directPath)) {
            return directPath;
        }
        try (Stream<Path> dateDirs = Files.list(root)) {
            Optional<Path> found = dateDirs
                    .filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}"))
                    .map(dir -> dir.resolve(conversationId))
                    .filter(Files::exists)
                    .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
        }
        return directPath;
    }

    @Operation(summary = "列出文件", description = "GET /api/chat-uploads 可选conversation过滤")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String conversation,
            @RequestParam(required = false) String path) {
        try {
            Path root = getRootPath();
            Files.createDirectories(root);

            Path targetPath;
            if (path != null && !path.isEmpty()) {
                targetPath = resolveUnderChatUploads(path);
            } else if (conversation != null && !conversation.isEmpty()) {
                targetPath = findConversationDirectory(root, conversation);
            } else {
                targetPath = root;
            }

            if (!Files.exists(targetPath)) {
                Map<String, Object> emptyResult = new LinkedHashMap<>();
                emptyResult.put("files", List.of());
                emptyResult.put("folders", List.of());
                return ResponseEntity.ok(emptyResult);
            }

            // 递归收集所有文件和文件夹
            List<Map<String, Object>> allFiles = new ArrayList<>();
            Set<String> allFolders = new TreeSet<>();

            try (Stream<Path> walk = Files.walk(targetPath)) {
                walk.forEach(p -> {
                    String relativePath = root.relativize(p).toString().replace(File.separator, "/");

                    if (Files.isDirectory(p)) {
                        // 跳过根目录本身
                        if (!p.equals(targetPath)) {
                            allFolders.add(relativePath);
                        }
                    } else {
                        // 是文件，构建详细信息
                        allFiles.add(buildFileInfo(p, root));
                    }
                });
            }

            // 按文件名排序
            allFiles.sort(Comparator.comparing(m -> (String) m.get("name")));

            // 文件夹排序
            List<String> sortedFolders = new ArrayList<>(allFolders);
            Collections.sort(sortedFolders);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("files", allFiles);
            result.put("folders", sortedFolders);

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("列出文件失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> buildFileInfo(Path path, Path root) {
        Map<String, Object> info = new LinkedHashMap<>();
        String relativePath = root.relativize(path).toString().replace(File.separator, "/");

        info.put("relativePath", relativePath);
        info.put("absolutePath", path.toAbsolutePath().toString());
        info.put("name", path.getFileName().toString());

        try {
            info.put("size", Files.size(path));
        } catch (IOException e) {
            info.put("size", 0L);
        }

        try {
            long modifiedMillis = Files.getLastModifiedTime(path).toMillis();
            info.put("modifiedUnix", modifiedMillis / 1000);
        } catch (IOException e) {
            info.put("modifiedUnix", 0L);
        }

        String date = extractDateFromPath(relativePath);
        info.put("date", date);

        String conversationId = extractConversationIdFromPath(relativePath);
        info.put("conversationId", conversationId != null ? conversationId : "");

        String subPath = extractSubPath(relativePath, date, conversationId);
        info.put("subPath", subPath);

        return info;
    }

    private String extractDateFromPath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return "";
        String[] parts = relativePath.split("/");
        if (parts.length > 0 && parts[0].matches("\\d{4}-\\d{2}-\\d{2}")) {
            return parts[0];
        }
        return "";
    }

    private String extractConversationIdFromPath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        String[] parts = relativePath.split("/");
        for (String part : parts) {
            if (part.matches(UUID_PATTERN)) {
                return part;
            }
        }
        return null;
    }

    private String extractSubPath(String relativePath, String date, String conversationId) {
        if (relativePath == null || relativePath.isEmpty()) return "";
        String result = relativePath;
        if (date != null && !date.isEmpty() && result.startsWith(date + "/")) {
            result = result.substring(date.length() + 1);
        }
        if (conversationId != null && !conversationId.isEmpty() && result.startsWith(conversationId + "/")) {
            result = result.substring(conversationId.length() + 1);
        }
        return result;
    }

    @GetMapping("/content")
    public ResponseEntity<?> getContent(@RequestParam String path) {
        try {
            Path absPath = resolveUnderChatUploads(path);
            if (!Files.exists(absPath)) {
                return ResponseEntity.notFound().build();
            }
            if (Files.isDirectory(absPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "cannot read directory content"));
            }
            long size = Files.size(absPath);
            if (size > MAX_EDIT_BYTES) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "file too large to read (max " + MAX_EDIT_BYTES + " bytes)"));
            }
            String content = Files.readString(absPath);
            return ResponseEntity.ok(Map.of("path", path, "content", content, "size", size));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/content")
    public ResponseEntity<?> putContent(@RequestBody Map<String, String> body) {
        try {
            String path = body.get("path");
            String content = body.get("content");
            if (path == null || path.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "path is required"));
            }
            Path absPath = resolveUnderChatUploads(path);
            if (content != null && content.length() > MAX_EDIT_BYTES) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "content too large (max " + MAX_EDIT_BYTES + " bytes)"));
            }
            Files.createDirectories(absPath.getParent());
            if (content != null) {
                Files.writeString(absPath, content);
            } else {
                Files.createFile(absPath);
            }
            return ResponseEntity.ok(Map.of("message", "OK", "path", path));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String relativeDir) {
        try {
            Path root = getRootPath();
            Files.createDirectories(root);

            Path targetPath;
            String dateStr = LocalDateTime.now().format(DATE_FORMATTER);

            if (relativeDir != null && !relativeDir.isEmpty()) {
                // 使用 relativeDir 保持文件夹结构（不自动加日期）
                targetPath = resolveUnderChatUploads(relativeDir);
                Files.createDirectories(targetPath.getParent());
                targetPath = handleFileNameConflict(targetPath);
            } else if (conversationId != null && !conversationId.isEmpty()) {
                // ⭐ 加日期：日期/conversationId/文件名
                Path targetDir = root.resolve(dateStr).resolve(conversationId);
                Files.createDirectories(targetDir);
                targetPath = targetDir.resolve(Objects.requireNonNull(file.getOriginalFilename()));
                targetPath = handleFileNameConflict(targetPath);
            } else {
                // 默认按日期保存
                Path targetDir = root.resolve(dateStr);
                Files.createDirectories(targetDir);
                targetPath = targetDir.resolve(Objects.requireNonNull(file.getOriginalFilename()));
                targetPath = handleFileNameConflict(targetPath);
            }

            if (file != null && !file.isEmpty()) {
                file.transferTo(targetPath.toFile());
                String relativePathResult = root.relativize(targetPath).toString().replace(File.separator, "/");
                return ResponseEntity.ok(Map.of(
                        "path", relativePathResult,
                        "size", file.getSize()
                ));
            }
            return ResponseEntity.badRequest().body(Map.of("error", "no file provided"));
        } catch (Exception e) {
            log.error("上传文件失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Path handleFileNameConflict(Path targetPath) {
        if (!Files.exists(targetPath)) return targetPath;
        String fileName = targetPath.getFileName().toString();
        String baseName, extension;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot);
        } else {
            baseName = fileName;
            extension = "";
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String newFileName = baseName + "_" + timestamp + extension;
        return targetPath.resolveSibling(newFileName);
    }

    @PostMapping("/mkdir")
    public ResponseEntity<?> mkdir(@RequestBody Map<String, String> body) {
        try {
            String parent = body.getOrDefault("parent", "");
            String name = body.get("name");
            if (name == null || name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
            }
            if (name.contains("/") || name.contains("\\")) {
                return ResponseEntity.badRequest().body(Map.of("error", "name must be a single segment"));
            }
            Path root = getRootPath();
            Path parentPath = parent != null && !parent.isEmpty() ? resolveUnderChatUploads(parent) : root;
            Path newDir = parentPath.resolve(name);
            Files.createDirectories(newDir);
            String relativePath = root.relativize(newDir).toString().replace(File.separator, "/");
            return ResponseEntity.ok(Map.of("path", relativePath));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> delete(@RequestBody Map<String, String> body) {
        try {
            String path = body.get("path");
            if (path == null || path.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "path is required"));
            }
            Path absPath = resolveUnderChatUploads(path);
            if (!Files.exists(absPath)) {
                return ResponseEntity.notFound().build();
            }
            if (Files.isDirectory(absPath)) {
                try (Stream<Path> entries = Files.walk(absPath)) {
                    entries.sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                }
            } else {
                Files.delete(absPath);
            }
            return ResponseEntity.ok(Map.of("message", "deleted"));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/rename")
    public ResponseEntity<?> rename(@RequestBody Map<String, String> body) {
        try {
            String path = body.get("path");
            String newName = body.get("newName");
            if (path == null || path.isEmpty() || newName == null || newName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "path and newName are required"));
            }
            Path absPath = resolveUnderChatUploads(path);
            Path newPath = absPath.resolveSibling(newName);
            Path root = getRootPath();
            if (!newPath.normalize().startsWith(root)) {
                return ResponseEntity.badRequest().body(Map.of("error", "new path escapes root"));
            }
            Files.move(absPath, newPath);
            String newRelativePath = root.relativize(newPath).toString().replace(File.separator, "/");
            return ResponseEntity.ok(Map.of("path", path, "newPath", newRelativePath));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam String path) {
        try {
            Path absPath = resolveUnderChatUploads(path);
            if (!Files.exists(absPath) || Files.isDirectory(absPath)) {
                return ResponseEntity.notFound().build();
            }
            String filename = absPath.getFileName().toString();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            byte[] content = Files.readAllBytes(absPath);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(content.length)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}