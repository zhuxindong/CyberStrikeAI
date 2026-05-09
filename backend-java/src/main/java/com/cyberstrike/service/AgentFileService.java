package com.cyberstrike.service;

import com.cyberstrike.dto.AgentListItem;
import com.cyberstrike.dto.AgentListResponse;
import com.cyberstrike.dto.MarkdownDirLoad;
import com.cyberstrike.entity.AgentMetadata;
import com.cyberstrike.entity.OrchestratorMarkdown;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentFileService {

    public static final String ORCHESTRATOR_MARKDOWN_FILENAME = "orchestrator.md";
    public static final String ORCHESTRATOR_PLAN_EXECUTE_MARKDOWN_FILENAME = "orchestrator-plan-execute.md";
    public static final String ORCHESTRATOR_SUPERVISOR_MARKDOWN_FILENAME = "orchestrator-supervisor.md";

    @Value("${cyberstrike.agent.agents-dir:./agents}")
    private String agentsDir;

    private final ObjectMapper yamlMapper;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}");

    public AgentFileService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        // 配置命名策略：自动转换驼峰和下划线
        this.yamlMapper.setPropertyNamingStrategy(
                com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE
        );
        // 忽略未知字段，增强健壮性
        this.yamlMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
    }

    @PostConstruct
    public void init() {
        ensureAgentsDirectory();
    }

    private void ensureAgentsDirectory() {
        Path dir = Paths.get(agentsDir);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("创建 agents 目录: {}", dir.toAbsolutePath());
            } catch (IOException e) {
                log.error("创建 agents 目录失败", e);
            }
        }
    }

    public String getAgentsDir() {
        return agentsDir;
    }

    // ==================== 列表接口 ====================

    public AgentListResponse listAgentsForUI() throws IOException {
        MarkdownDirLoad load = loadMarkdownAgentsDir();
        List<AgentListItem> items = new ArrayList<>();

        if (load.getOrchestrator() != null) {
            OrchestratorMarkdown orch = load.getOrchestrator();
            items.add(AgentListItem.builder()
                    .description(orch.getDescription())
                    .filename(orch.getFilename())
                    .id(orch.getEinoName())
                    .isOrchestrator(true)
                    .kind("orchestrator")
                    .name(orch.getDisplayName())
                    .build());
        }

        if (load.getOrchestratorPlanExecute() != null) {
            OrchestratorMarkdown orch = load.getOrchestratorPlanExecute();
            items.add(AgentListItem.builder()
                    .description(orch.getDescription())
                    .filename(orch.getFilename())
                    .id(orch.getEinoName())
                    .isOrchestrator(true)
                    .kind("plan_execute")
                    .name(orch.getDisplayName())
                    .build());
        }

        if (load.getOrchestratorSupervisor() != null) {
            OrchestratorMarkdown orch = load.getOrchestratorSupervisor();
            items.add(AgentListItem.builder()
                    .description(orch.getDescription())
                    .filename(orch.getFilename())
                    .id(orch.getEinoName())
                    .isOrchestrator(true)
                    .kind("supervisor")
                    .name(orch.getDisplayName())
                    .build());
        }

        if (load.getSubAgents() != null) {
            for (AgentMetadata sub : load.getSubAgents()) {
                items.add(AgentListItem.builder()
                        .description(sub.getDescription())
                        .filename(sub.getFilename())
                        .id(sub.getId())
                        .isOrchestrator(false)
                        .kind(sub.getKind() != null ? sub.getKind() : "")
                        .name(sub.getName())
                        .build());
            }
        }

        items.sort(Comparator.comparing(AgentListItem::getName));

        return AgentListResponse.builder()
                .agents(items)
                .dir(Paths.get(agentsDir).toAbsolutePath().toString())
                .build();
    }

    public AgentMetadata getAgent(String filename) throws IOException {
        Path filePath = Paths.get(agentsDir, filename);
        if (!Files.exists(filePath)) {
            throw new IOException("Agent 文件不存在: " + filename);
        }
        String content = Files.readString(filePath);
        return parseMarkdownSubAgent(filename, content);
    }

    public AgentMetadata createAgent(String filename, AgentMetadata metadata) throws IOException {
        Path filePath = Paths.get(agentsDir, filename);
        if (Files.exists(filePath)) {
            throw new IOException("Agent 文件已存在: " + filename);
        }

        if (wantsMarkdownOrchestrator(filename, metadata.getKind(), null)) {
            checkOrchestratorConflict(filename, metadata.getKind());
        }

        metadata.setFilename(filename);
        byte[] content = buildMarkdownFile(metadata);
        Files.write(filePath, content);

        log.info("创建 Agent: {}", filename);
        return metadata;
    }

    public AgentMetadata updateAgent(String filename, AgentMetadata metadata) throws IOException {
        Path filePath = Paths.get(agentsDir, filename);
        if (!Files.exists(filePath)) {
            throw new IOException("Agent 文件不存在: " + filename);
        }

        String newFilename = metadata.getFilename();
        if (newFilename != null && !filename.equals(newFilename)) {
            Path newFilePath = Paths.get(agentsDir, newFilename);
            if (Files.exists(newFilePath)) {
                throw new IOException("目标文件名已存在: " + newFilename);
            }
            Files.move(filePath, newFilePath);
            filePath = newFilePath;
            filename = newFilename;
        }

        if (wantsMarkdownOrchestrator(filename, metadata.getKind(), null)) {
            checkOrchestratorConflict(filename, metadata.getKind());
        }

        metadata.setFilename(filename);
        byte[] content = buildMarkdownFile(metadata);
        Files.write(filePath, content);

        log.info("更新 Agent: {}", filename);
        return metadata;
    }

    public boolean deleteAgent(String filename) throws IOException {
        Path filePath = Paths.get(agentsDir, filename);
        if (!Files.exists(filePath)) {
            return false;
        }

        AgentMetadata metadata = getAgent(filename);
        if (metadata.getKind() != null &&
                (metadata.getKind().equals("orchestrator") ||
                        metadata.getKind().equals("plan_execute") ||
                        metadata.getKind().equals("supervisor"))) {
            throw new IOException("不能删除主代理");
        }

        Files.delete(filePath);
        log.info("删除 Agent: {}", filename);
        return true;
    }

    // ==================== 内部方法 ====================

    private MarkdownDirLoad loadMarkdownAgentsDir() throws IOException {
        return loadMarkdownAgentsDir(agentsDir);
    }

    private MarkdownDirLoad loadMarkdownAgentsDir(String dir) throws IOException {
        List<AgentMetadata> subAgentsList = new ArrayList<>();
        List<MarkdownDirLoad.FileAgent> fileEntriesList = new ArrayList<>();
        OrchestratorMarkdown orchestratorObj = null;
        OrchestratorMarkdown orchestratorPlanExecuteObj = null;
        OrchestratorMarkdown orchestratorSupervisorObj = null;

        List<String> names = collectMarkdownBasenames(dir);

        for (String name : names) {
            Path filePath = Paths.get(dir, name);
            String content = Files.readString(filePath);

            ParseResult parseResult = parseMarkdownAgentRaw(name, content);
            String kind = getOrchestratorMarkdownKind(name);

            switch (kind) {
                case "plan_execute":
                    if (orchestratorPlanExecuteObj != null) {
                        throw new IOException("仅能定义一个 " + ORCHESTRATOR_PLAN_EXECUTE_MARKDOWN_FILENAME);
                    }
                    orchestratorPlanExecuteObj = orchestratorFromParsed(name, parseResult.fm, parseResult.body);
                    fileEntriesList.add(MarkdownDirLoad.FileAgent.builder()
                            .filename(name)
                            .config(orchestratorConfigFromOrchestrator(orchestratorPlanExecuteObj))
                            .orchestrator(true)
                            .build());
                    continue;

                case "supervisor":
                    if (orchestratorSupervisorObj != null) {
                        throw new IOException("仅能定义一个 " + ORCHESTRATOR_SUPERVISOR_MARKDOWN_FILENAME);
                    }
                    orchestratorSupervisorObj = orchestratorFromParsed(name, parseResult.fm, parseResult.body);
                    fileEntriesList.add(MarkdownDirLoad.FileAgent.builder()
                            .filename(name)
                            .config(orchestratorConfigFromOrchestrator(orchestratorSupervisorObj))
                            .orchestrator(true)
                            .build());
                    continue;
            }

            if (isOrchestratorMarkdown(name, parseResult.fm)) {
                if (orchestratorObj != null) {
                    throw new IOException("仅能定义一个主代理（Deep 协调者）");
                }
                orchestratorObj = orchestratorFromParsed(name, parseResult.fm, parseResult.body);
                fileEntriesList.add(MarkdownDirLoad.FileAgent.builder()
                        .filename(name)
                        .config(orchestratorConfigFromOrchestrator(orchestratorObj))
                        .orchestrator(true)
                        .build());
                continue;
            }

            AgentMetadata sub = subAgentFromFrontMatter(name, parseResult.fm, parseResult.body);
            subAgentsList.add(sub);
            fileEntriesList.add(MarkdownDirLoad.FileAgent.builder()
                    .filename(name)
                    .config(sub)
                    .orchestrator(false)
                    .build());
        }

        return MarkdownDirLoad.builder()
                .subAgents(subAgentsList)
                .orchestrator(orchestratorObj)
                .orchestratorPlanExecute(orchestratorPlanExecuteObj)
                .orchestratorSupervisor(orchestratorSupervisorObj)
                .fileEntries(fileEntriesList)
                .build();
    }

    private List<String> collectMarkdownBasenames(String dir) throws IOException {
        Path dirPath = Paths.get(dir);
        if (!Files.exists(dirPath)) {
            return Collections.emptyList();
        }

        try (var stream = Files.list(dirPath)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".md"))
                    .filter(name -> !name.equalsIgnoreCase("README.md"))
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private String getOrchestratorMarkdownKind(String filename) {
        String base = new File(filename).getName();
        if (base.equalsIgnoreCase(ORCHESTRATOR_PLAN_EXECUTE_MARKDOWN_FILENAME)) {
            return "plan_execute";
        }
        if (base.equalsIgnoreCase(ORCHESTRATOR_SUPERVISOR_MARKDOWN_FILENAME)) {
            return "supervisor";
        }
        if (base.equalsIgnoreCase(ORCHESTRATOR_MARKDOWN_FILENAME)) {
            return "deep";
        }
        return "";
    }

    private boolean isOrchestratorMarkdown(String filename, FrontMatter fm) {
        String base = new File(filename).getName();
        String kind = getOrchestratorMarkdownKind(base);
        if ("plan_execute".equals(kind) || "supervisor".equals(kind)) {
            return false;
        }
        if (base.equalsIgnoreCase(ORCHESTRATOR_MARKDOWN_FILENAME)) {
            return true;
        }
        return fm != null && "orchestrator".equalsIgnoreCase(fm.getKind());
    }

    private boolean wantsMarkdownOrchestrator(String filename, String kindField, String rawContent) {
        String base = new File(filename).getName();
        if (!getOrchestratorMarkdownKind(base).isEmpty()) {
            return true;
        }
        if ("orchestrator".equalsIgnoreCase(kindField)) {
            return true;
        }
        if (base.equalsIgnoreCase(ORCHESTRATOR_MARKDOWN_FILENAME)) {
            return true;
        }
        return false;
    }

    private void checkOrchestratorConflict(String filename, String kind) throws IOException {
        MarkdownDirLoad load = loadMarkdownAgentsDir();
        String baseKind = getOrchestratorMarkdownKind(filename);
        if (!baseKind.isEmpty()) {
            kind = baseKind;
        }

        switch (kind) {
            case "plan_execute":
                if (load.getOrchestratorPlanExecute() != null &&
                        !load.getOrchestratorPlanExecute().getFilename().equals(filename)) {
                    throw new IOException("已存在 plan_execute 主代理");
                }
                break;
            case "supervisor":
                if (load.getOrchestratorSupervisor() != null &&
                        !load.getOrchestratorSupervisor().getFilename().equals(filename)) {
                    throw new IOException("已存在 supervisor 主代理");
                }
                break;
            default:
                if (load.getOrchestrator() != null &&
                        !load.getOrchestrator().getFilename().equals(filename)) {
                    throw new IOException("已存在 Deep 主代理");
                }
        }
    }

    private ParseResult parseMarkdownAgentRaw(String filename, String content) throws IOException {
        String[] parts = splitFrontMatter(content);
        String fmStr = parts[0];
        String body = parts[1];

        if (fmStr.trim().isEmpty()) {
            throw new IOException(filename + " 无 YAML front matter");
        }

        FrontMatter fm = yamlMapper.readValue(fmStr, FrontMatter.class);
        return new ParseResult(fm, body);
    }

    private String[] splitFrontMatter(String content) {
        String s = content.trim();
        if (!s.startsWith("---")) {
            return new String[]{"", s};
        }
        String rest = s.substring(3);
        rest = rest.replaceFirst("^[\r\n]+", "");
        int end = rest.indexOf("\n---");
        if (end < 0) {
            return new String[]{"", s};
        }
        String fm = rest.substring(0, end).trim();
        String body = rest.substring(end + 4).trim();
        body = body.replaceFirst("^[\r\n]+", "");
        return new String[]{fm, body};
    }

    private AgentMetadata parseMarkdownSubAgent(String filename, String content) throws IOException {
        ParseResult result = parseMarkdownAgentRaw(filename, content);
        String kind = getOrchestratorMarkdownKind(filename);
        if (!kind.isEmpty()) {
            OrchestratorMarkdown orch = orchestratorFromParsed(filename, result.fm, result.body);
            return orchestratorConfigFromOrchestrator(orch);
        }
        if (isOrchestratorMarkdown(filename, result.fm)) {
            OrchestratorMarkdown orch = orchestratorFromParsed(filename, result.fm, result.body);
            return orchestratorConfigFromOrchestrator(orch);
        }
        return subAgentFromFrontMatter(filename, result.fm, result.body);
    }

    private OrchestratorMarkdown orchestratorFromParsed(String filename, FrontMatter fm, String body) {
        String display = fm.getName() != null ? fm.getName().trim() : "Orchestrator";
        if (display.isEmpty()) display = "Orchestrator";

        String rawId = fm.getId() != null ? fm.getId().trim() : "";
        if (rawId.isEmpty()) rawId = slugId(display);

        String eino = sanitizeEinoAgentId(rawId);

        return OrchestratorMarkdown.builder()
                .filename(new File(filename).getName())
                .einoName(eino)
                .displayName(display)
                .description(fm.getDescription() != null ? fm.getDescription().trim() : "")
                .instruction(body != null ? body.trim() : "")
                .build();
    }

    private AgentMetadata orchestratorConfigFromOrchestrator(OrchestratorMarkdown o) {
        if (o == null) return null;
        return AgentMetadata.builder()
                .id(o.getEinoName())
                .name(o.getDisplayName())
                .description(o.getDescription())
                .instruction(o.getInstruction())
                .kind("orchestrator")
                .build();
    }

    private AgentMetadata subAgentFromFrontMatter(String filename, FrontMatter fm, String body) throws IOException {
        String name = fm.getName() != null ? fm.getName().trim() : "";
        if (name.isEmpty()) {
            throw new IOException(filename + " 缺少 name 字段");
        }

        String id = fm.getId() != null ? fm.getId().trim() : "";
        if (id.isEmpty()) {
            id = slugId(name);
        }

        return AgentMetadata.builder()
                .id(id)
                .name(name)
                .description(fm.getDescription() != null ? fm.getDescription().trim() : "")
                .instruction(body != null ? body.trim() : "")
                .tools(parseToolsField(fm.getTools()))
                .maxIterations(fm.getMaxIterations())
                .bindRole(fm.getBindRole() != null ? fm.getBindRole().trim() : "")
                .kind(fm.getKind() != null ? fm.getKind().trim() : "")
                .filename(filename)
                .build();
    }

    public byte[] buildMarkdownFile(AgentMetadata sub) throws IOException {
        FrontMatter fm = FrontMatter.builder()
                .name(sub.getName())
                .id(sub.getId())
                .description(sub.getDescription())
                .maxIterations(sub.getMaxIterations())
                .bindRole(sub.getBindRole())
                .kind(sub.getKind())
                .tools(sub.getTools())
                .build();

        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        String yamlStr = yamlMapper.writeValueAsString(fm);
        sb.append(yamlStr);
        if (!yamlStr.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("---\n\n");
        sb.append(sub.getInstruction() != null ? sub.getInstruction().trim() : "");
        if (sub.getInstruction() != null && !sub.getInstruction().endsWith("\n") && !sub.getInstruction().isEmpty()) {
            sb.append("\n");
        }

        return sb.toString().getBytes();
    }

    private List<String> parseToolsField(Object v) {
        if (v == null) return null;
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            return list.stream()
                    .filter(x -> x instanceof String)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        if (v instanceof String) {
            String str = ((String) v).trim();
            if (str.isEmpty()) return null;
            return Arrays.stream(str.split("[,;|]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return null;
    }

    private String slugId(String name) {
        if (name == null) return "agent";
        String s = name.trim().toLowerCase();
        StringBuilder b = new StringBuilder();
        boolean lastDash = false;
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                b.append(c);
                lastDash = false;
            } else if (c == ' ' || c == '_' || c == '/' || c == '.') {
                if (!lastDash && b.length() > 0) {
                    b.append('-');
                    lastDash = true;
                }
            }
        }
        String result = b.toString().replaceAll("^-+", "").replaceAll("-+$", "");
        return result.isEmpty() ? "agent" : result;
    }

    private String sanitizeEinoAgentId(String s) {
        if (s == null) return "cyberstrike-deep";
        s = s.trim().toLowerCase();
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                b.append(c);
            }
        }
        String result = b.toString().replaceAll("^-+", "").replaceAll("-+$", "");
        return result.isEmpty() ? "cyberstrike-deep" : result;
    }

    public String replaceVariables(String content, Map<String, String> variables) {
        if (content == null || content.isEmpty() || variables == null || variables.isEmpty()) {
            return content;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, matcher.group(0));
            replacement = Matcher.quoteReplacement(replacement);
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    // 内部类
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ParseResult {
        private FrontMatter fm;
        private String body;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FrontMatter {
        private String name;
        private String id;
        private String description;
        private Object tools;
        private int maxIterations;
        private String bindRole;
        private String kind;
    }
}