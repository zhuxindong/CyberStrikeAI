package com.cyberstrike.tool;

import com.cyberstrike.entity.McpServer;
import com.cyberstrike.mcp.McpExecutor;
import com.cyberstrike.repository.McpServerRepository;
import com.cyberstrike.service.SkillsStatsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一工具注册中心
 * 支持硬编码工具和 YAML 工具
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> builtinTools = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YamlToolLoader yamlToolLoader;
    private final com.cyberstrike.service.KnowledgeService knowledgeService;
    private final com.cyberstrike.service.PythonVenvService pythonVenvService;
    private final McpServerRepository mcpServerRepository;
    private final com.cyberstrike.skills.SkillsManager skillsManager;
    private final SkillsStatsService skillsStatsService;

    public ToolRegistry(YamlToolLoader yamlToolLoader,
            com.cyberstrike.service.KnowledgeService knowledgeService,
            com.cyberstrike.service.PythonVenvService pythonVenvService,
                        McpServerRepository mcpServerRepository,
            com.cyberstrike.skills.SkillsManager skillsManager,
            SkillsStatsService skillsStatsService) {
        this.yamlToolLoader = yamlToolLoader;
        this.knowledgeService = knowledgeService;
        this.pythonVenvService = pythonVenvService;
        this.mcpServerRepository = mcpServerRepository;
        this.skillsManager = skillsManager;
        this.skillsStatsService = skillsStatsService;
        registerBuiltinTools();
        // 注册Skills工具
        registerSkillsTools();
        // 统计知识库工具数量
        long knowledgeToolsCount = builtinTools.values().stream()
                .filter(t -> "Knowledge".equals(t.toolType()))
                .count();
        log.info("工具注册完成: {} 个内置工具(含 {} 个知识库工具), {} 个 YAML 工具",
                builtinTools.size(), knowledgeToolsCount, yamlToolLoader.getAllTools().size());
    }
    
    /**
     * 注册Skills工具 - 每个skill注册为独立工具
     */
    private void registerSkillsTools() {
        if (skillsManager == null) {
            log.warn("SkillsManager 未注入，跳过注册 Skills 工具");
            return;
        }
        try {
            // list_skills 工具
            String listSkillsParams = "{}";

            // 为每个skill注册独立的工具
            List<String> skillNames = skillsManager.listSkills();
            for (String skillName : skillNames) {
                String skillToolName = skillName;
                // 获取skill的描述
                com.cyberstrike.skills.Skill skill = skillsManager.loadSkill(skillName);
                String description = skill != null && skill.getDescription() != null ? 
                    skill.getDescription() : "读取 " + skillName + " skill的详细内容。";
                
                builtinTools.put(skillToolName, new ToolDefinition(
                    skillToolName,
                    description,
                    objectMapper.readTree("{}"),
                    args -> {
                        try {
                            com.cyberstrike.skills.Skill loadedSkill = skillsManager.loadSkill(skillName);
                            if (loadedSkill == null) {
                                // 记录失败
                                if (skillsStatsService != null) {
                                    skillsStatsService.recordSkillCall(skillName, false);
                                }
                                return "读取skill失败: " + skillName;
                            }
                            
                            // 记录成功调用
                            if (skillsStatsService != null) {
                                skillsStatsService.recordSkillCall(skillName, true);
                            }
                            
                            StringBuilder result = new StringBuilder();
                            result.append("## Skill: ").append(loadedSkill.getName()).append("\n\n");
                            if (loadedSkill.getDescription() != null) {
                                result.append("**描述**: ").append(loadedSkill.getDescription()).append("\n\n");
                            }
                            result.append("---\n\n");
                            result.append(loadedSkill.getContent());
                            result.append("\n\n---\n\n");
                            result.append("*Skill路径: ").append(loadedSkill.getPath()).append("*");
                            return result.toString();
                        } catch (Exception e) {
                            log.error("读取skill失败: " + skillName, e);
                            // 记录失败
                            if (skillsStatsService != null) {
                                skillsStatsService.recordSkillCall(skillName, false);
                            }
                            return "读取skill失败: " + skillName + " - " + e.getMessage();
                        }
                    },
                    "Skills"
                ));
            }
            
            log.info("Skills 工具注册成功: {} 个", skillNames.size());
        } catch (Exception e) {
            log.error("注册 Skills 工具失败: ", e);
        }
    }

    /**
     * 注册内置工具（仅保留必要的基础工具）
     */
    private void registerBuiltinTools() {

        // ==================================================================
        // 核心修复：通用的命令执行函数 (解决 Windows 中文乱码)
        // 使用 GBK 编码读取流，防止 whois/nslookup/tracert 输出乱码
        // ==================================================================
        BiFunction<JsonNode, String, String> executeCommandWin = (args, command) -> {
            StringBuilder output = new StringBuilder();
            Process process = null;
            BufferedReader reader = null;

            try {
                // 1. 构建进程 (使用 cmd /c 执行命令)
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
                pb.redirectErrorStream(true); // 合并错误流和输出流
                process = pb.start();

                // 2. 核心修复：强制使用 GBK 编码读取 (Windows 控制台默认编码)
                // 如果是 Linux/Mac，这里应改为 UTF-8
                InputStreamReader isr = new InputStreamReader(process.getInputStream(), "GBK");
                reader = new BufferedReader(isr);

                // 3. 读取输出
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                // 4. 等待进程结束
                process.waitFor(10, TimeUnit.SECONDS); // 设置超时，防止挂起

            } catch (Exception e) {
                output.append("Command Execution Error: ").append(e.getMessage());
            } finally {
                // 关闭资源
                try { if (reader != null) reader.close(); } catch (IOException e) { /* 忽略 */ }
                if (process != null) process.destroy();
            }

            return output.toString();
        };

        // 保留一些简单的基础工具作为后备
        registerBuiltinTool("ping_host", "Ping 目标主机检查可达性",
                """
                    {"type":"object", "properties":{
                        "target":{"type":"string", "description":"目标 IP 或域名"},
                        "count":{"type":"integer", "description":"Ping 次数，默认 4"}
                    }, "required":["target"]}
                """,
                (args) -> {
                    String target = args.get("target").asText();
                    int count = args.has("count") ? args.get("count").asInt() : 4;

                    // 构建命令字符串
                    String cmd = "ping -n " + count + " " + target;

                    // 直接复用 executeCommandWin，无需重复写乱码修复逻辑
                    return executeCommandWin.apply(args, cmd);
                });


        // ==================================================================
        // 网络侦查工具 (使用上面修复乱码的函数)
        // ==================================================================

        // WHOIS 查询
        registerBuiltinTool("whois_lookup", "WHOIS 查询，获取域名注册信息",
                """
                {"type":"object", "properties":{
                    "domain":{"type":"string", "description":"要查询的域名，例如 example.com"}
                }, "required":["domain"]}
                """,
                (args) -> {
                    String domain = args.get("domain").asText();
                    // 直接拼接命令并执行
                    String cmd = "whois " + domain;
                    return executeCommandWin.apply(args, cmd);
                });

        // DNS 查询 (nslookup)
        registerBuiltinTool("dig_dns", "DNS 查询工具",
                """
                {"type":"object", "properties":{
                    "domain":{"type":"string", "description":"要查询的域名"},
                    "type":{"type":"string", "description":"记录类型，如 A, MX, NS, TXT"}
                }, "required":["domain"]}
                """,
                (args) -> {
                    String domain = args.get("domain").asText();
                    String type = args.has("type") ? args.get("type").asText() : "A";
                    String cmd = "nslookup -type=" + type + " " + domain;
                    return executeCommandWin.apply(args, cmd);
                });

        // 路由追踪 (tracert)
        registerBuiltinTool("traceroute", "路由追踪",
                """
                {"type":"object", "properties":{
                    "target":{"type":"string", "description":"目标 IP 或域名"}
                }, "required":["target"]}
                """,
                (args) -> {
                    String target = args.get("target").asText();
                    String cmd = "tracert " + target;
                    return executeCommandWin.apply(args, cmd);
                });

        // HTTP 请求 (curl)
        // 注意：Curl 获取的网页通常是 UTF-8，但命令行提示符可能是 GBK
        // 这里为了防止命令错误提示乱码，依然使用 GBK 执行
        registerBuiltinTool("curl_request", "发送 HTTP 请求",
                """
                {"type":"object", "properties":{
                    "url":{"type":"string", "description":"目标 URL"},
                    "method":{"type":"string", "description":"HTTP 方法，默认 GET"},
                    "data":{"type":"string", "description":"POST 数据"}
                }, "required":["url"]}
                """,
                (args) -> {
                    String url = args.get("url").asText();
                    String method = args.has("method") ? args.get("method").asText() : "GET";
                    StringBuilder cmd = new StringBuilder("curl -s -X ").append(method);

                    if (args.has("data")) {
                        cmd.append(" -d \"").append(args.get("data").asText()).append("\"");
                    }
                    cmd.append(" \"").append(url).append("\"");

                    // 1. 先执行命令
                    String rawResult = executeCommandWin.apply(args, cmd.toString());

                    // 2. 在这里加一个“过滤器”
                    if (rawResult == null) return "NULL";

                    // --- 简单粗暴的防护 ---
                    // 如果结果里包含 "PK" 开头，或者包含大量不可见字符，说明是文件
                    if (rawResult.length() >= 2 &&
                            rawResult.charAt(0) == 'P' &&
                            rawResult.charAt(1) == 'K') {
                        return "Success: File downloaded (type: application/zip or executable). " +
                                "Content not shown to prevent encoding errors.";
                    }

                    // 如果内容太长，截断
                    if (rawResult.length() > 5000) {
                        return rawResult.substring(0, 5000) + "... [Output cut off]";
                    }

                    return rawResult; // 正常返回
                });

        // ==================================================================
        // Python 开发与执行工具
        // 注意：Python 的乱码修复需要在 pythonVenvService 内部实现
        // (确保其内部也是用 GBK 读取流，逻辑同上)
        // ==================================================================
        if (pythonVenvService != null) {
            registerBuiltinTool("install_python_package", "在虚拟环境中安装 Python 包",
                    """
                    {"type":"object", "properties":{
                        "package":{"type":"string", "description":"要安装的 Python 包名，如 requests, numpy"},
                        "env_name":{"type":"string", "description":"虚拟环境名称，默认 default"},
                        "additional_args":{"type":"string", "description":"额外的 pip 参数，如 --upgrade"}
                    }, "required":["package"]}
                    """,
                    (args) -> {
                        String pkg = args.get("package").asText();
                        String envName = args.has("env_name") ? args.get("env_name").asText() : "default";
                        String additionalArgs = args.has("additional_args") ? args.get("additional_args").asText() : "";
                        return pythonVenvService.installPackage(pkg, envName, additionalArgs);
                    });

            registerBuiltinTool("execute_python_script", "在虚拟环境中执行 Python 脚本",
                    """
                    {"type":"object", "properties":{
                        "script":{"type":"string", "description":"要执行的 Python 脚本内容"},
                        "env_name":{"type":"string", "description":"虚拟环境名称，默认 default"},
                        "additional_args":{"type":"string", "description":"额外的 Python 参数"}
                    }, "required":["script"]}
                    """,
                    (args) -> {
                        String script = args.get("script").asText();
                        String envName = args.has("env_name") ? args.get("env_name").asText() : "default";
                        String additionalArgs = args.has("additional_args") ? args.get("additional_args").asText() : "";
                        return pythonVenvService.executeScript(script, envName, additionalArgs);
                    });
        }

        // ==================================================================
        // 知识库工具注册
        // ==================================================================
        if (knowledgeService != null) {
            // 注册知识库工具 - 类似 Skills 模块的注册方式
            registerKnowledgeTools();
        }

    }

    /**
     * 注册知识库工具 - 类似 Skills 模块的注册方式
     */
    private void registerKnowledgeTools() {
        if (knowledgeService == null) {
            log.warn("KnowledgeService 未注入，跳过注册知识库工具");
            return;
        }

        // 工具1: 获取所有风险类型列表
        registerBuiltinTool("list_knowledge_risk_types", "获取知识库中所有可用的风险类型（risk_type）列表。在搜索知识库之前，可以先调用此工具获取可用的风险类型，然后使用正确的风险类型进行精确搜索，这样可以大幅减少检索时间并提高检索准确性。",
                """
                {"type":"object", "properties":{}, "required":[]}
                """,
                (args) -> {
                    try {
                        List<String> categories = knowledgeService.getCategories();
                        if (categories.isEmpty()) {
                            return "知识库中暂无风险类型。";
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("知识库中共有 %d 个风险类型：\n\n", categories.size()));
                        for (int i = 0; i < categories.size(); i++) {
                            sb.append(String.format("%d. %s\n", i + 1, categories.get(i)));
                        }
                        sb.append("\n提示：在调用 search_knowledge_base 工具时，可以使用上述风险类型之一作为 risk_type 参数，以缩小搜索范围并提高检索效率。");
                        return sb.toString();
                    } catch (Exception e) {
                        return "获取风险类型列表失败: " + e.getMessage();
                    }
                },
                "Knowledge");  // 设置 toolType 为 Knowledge

        // 工具2: 搜索知识库
        registerBuiltinTool("search_knowledge_base", "在知识库中搜索相关的安全知识。当你需要了解特定漏洞类型、攻击技术、检测方法等安全知识时，可以使用此工具进行检索。工具使用向量检索和混合搜索技术，能够根据查询内容的语义相似度和关键词匹配，自动找到最相关的知识片段。建议：在搜索前可以先调用 list_knowledge_risk_types 工具获取可用的风险类型，然后使用正确的 risk_type 参数进行精确搜索，这样可以大幅减少检索时间。",
                """
                {"type":"object", "properties":{
                    "query":{"type":"string", "description":"搜索查询内容，描述你想要了解的安全知识主题"},
                    "risk_type":{"type":"string", "description":"可选：指定风险类型（如：SQL注入、XSS、文件上传等）。建议先调用 list_knowledge_risk_types 工具获取可用的风险类型列表，然后使用正确的风险类型进行精确搜索，这样可以大幅减少检索时间。如果不指定则搜索所有类型。"}
                }, "required":["query"]}
                """,
                (args) -> {
                    String query = args.has("query") ? args.get("query").asText() : "";
                    String riskType = args.has("risk_type") ? args.get("risk_type").asText() : "";

                    if (query == null || query.isEmpty()) {
                        return "错误: 查询参数不能为空";
                    }

                    try {
                        log.info("执行知识库检索, query: {}, riskType: {}", query, riskType);

                        // 执行混合检索
                        List<com.cyberstrike.service.KnowledgeRetriever.RetrievalResult> results =
                                knowledgeService.searchWithChunks(query, riskType, 5, 0.7);

                        if (results.isEmpty()) {
                            // 记录知识检索统计（无结果）
                            knowledgeService.recordKnowledgeRetrieval(query, new java.util.ArrayList<>());
                            return String.format("未找到与查询 '%s' 相关的知识。建议：\n1. 尝试使用不同的关键词\n2. 检查风险类型是否正确\n3. 确认知识库中是否包含相关内容", query);
                        }

                        // 按文档分组结果
                        Map<String, List<com.cyberstrike.service.KnowledgeRetriever.RetrievalResult>> resultsByItem = new LinkedHashMap<>();
                        for (com.cyberstrike.service.KnowledgeRetriever.RetrievalResult result : results) {
                            String itemId = result.getItem().getId();
                            resultsByItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(result);
                        }

                        // 收集检索到的知识项 ID 列表
                        List<String> retrievedItemIds = new ArrayList<>(resultsByItem.keySet());

                        // 记录知识检索统计（类似 skill 模块的记录方式）
                        knowledgeService.recordKnowledgeRetrieval(query, retrievedItemIds);

                        // 按最高混合分数排序文档组
                        List<Map.Entry<String, List<com.cyberstrike.service.KnowledgeRetriever.RetrievalResult>>> sortedGroups =
                                resultsByItem.entrySet().stream()
                                        .sorted((a, b) -> {
                                            double maxScoreA = a.getValue().stream()
                                                    .mapToDouble(com.cyberstrike.service.KnowledgeRetriever.RetrievalResult::getScore).max().orElse(0);
                                            double maxScoreB = b.getValue().stream()
                                                    .mapToDouble(com.cyberstrike.service.KnowledgeRetriever.RetrievalResult::getScore).max().orElse(0);
                                            return Double.compare(maxScoreB, maxScoreA);
                                        })
                                        .collect(Collectors.toList());

                        // 构建结果字符串
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("找到 %d 条相关知识（包含上下文扩展）：\n\n", results.size()));

                        int resultIndex = 1;
                        for (Map.Entry<String, List<com.cyberstrike.service.KnowledgeRetriever.RetrievalResult>> entry : sortedGroups) {
                            List<com.cyberstrike.service.KnowledgeRetriever.RetrievalResult> itemResults = entry.getValue();

                            // 找到混合分数最高的作为主结果
                            com.cyberstrike.service.KnowledgeRetriever.RetrievalResult mainResult = itemResults.stream()
                                    .max(Comparator.comparingDouble(com.cyberstrike.service.KnowledgeRetriever.RetrievalResult::getScore))
                                    .orElse(itemResults.get(0));

                            // 按 chunk_index 排序
                            itemResults.sort(Comparator.comparingInt(r -> r.getChunk().getChunkIndex()));

                            // 显示主结果
                            sb.append(String.format("--- 结果 %d (相似度: %.2f%%, 混合分数: %.2f%%) ---\n",
                                    resultIndex, mainResult.getSimilarity() * 100, mainResult.getScore() * 100));
                            sb.append(String.format("来源: [%s] %s (ID: %s)\n",
                                    mainResult.getItem().getCategory(), mainResult.getItem().getTitle(), mainResult.getItem().getId()));

                            // 显示内容片段
                            if (itemResults.size() == 1) {
                                sb.append(String.format("内容片段:\n%s\n", mainResult.getChunk().getChunkText()));
                            } else {
                                sb.append("内容片段（按文档顺序）:\n");
                                for (int i = 0; i < itemResults.size(); i++) {
                                    com.cyberstrike.service.KnowledgeRetriever.RetrievalResult r = itemResults.get(i);
                                    String marker = r.getChunk().getId().equals(mainResult.getChunk().getId()) ? " [主匹配]" : "";
                                    sb.append(String.format("  [片段 %d%s]\n%s\n", i + 1, marker, r.getChunk().getChunkText()));
                                }
                            }
                            sb.append("\n");

                            retrievedItemIds.add(entry.getKey());
                            resultIndex++;
                        }

                        // 添加元数据
                        if (!retrievedItemIds.isEmpty()) {
                            sb.append(String.format("\n<!-- METADATA: {\"retrievedItemIDs\": %s} -->",
                                    retrievedItemIds));
                        }

                        return sb.toString();
                    } catch (Exception e) {
                        log.error("知识库检索失败", e);
                        // 记录检索失败
                        knowledgeService.recordKnowledgeRetrieval(query, false);
                        return "检索失败: " + e.getMessage();
                    }
                },
                "Knowledge");  // 设置 toolType 为 Knowledge
    }

    /**
     * 获取所有工具定义（合并内置和 YAML(已启用)）
     */
    public Collection<ToolDefinition> getTools() {
        List<ToolDefinition> allTools = new ArrayList<>(builtinTools.values());

        // 添加 YAML 工具
        for (YamlToolDefinition yamlTool : yamlToolLoader.getEnabledTools()) {
            // 跳过已存在的内置工具
            if (builtinTools.containsKey(yamlTool.getName())) {
                continue;
            }

            try {
                JsonNode params = objectMapper.valueToTree(yamlTool.toFunctionParameters());
                allTools.add(new ToolDefinition(
                        yamlTool.getName(),
                        yamlTool.getShortDescription() != null ? yamlTool.getShortDescription()
                                : yamlTool.getDescription(),
                        params,
                        null,
                        "MCP" // YAML 工具类型
                ));
            } catch (Exception e) {
                log.error("转换 YAML 工具失败: {}", yamlTool.getName(), e);
            }
        }

        return allTools;
    }

    /**
     * 获取所有工具定义
     */
    public Collection<ToolDefinition> getToolsAll() {
        List<ToolDefinition> allTools = new ArrayList<>(builtinTools.values());

        // --- 2. 添加 MCP 工具 (新增逻辑) ---
        try {
            // 从数据库查出所有启用且连接正常的 MCP 服务
            List<McpServer> mcpServers = mcpServerRepository.findByStatus("connected");

            for (McpServer server : mcpServers) {
                // 跳过已存在的同名工具
                if (builtinTools.containsKey(server.getName())) {
                    log.warn("工具名冲突，跳过 MCP 工具: {}", server.getName());
                    continue;
                }

                // --- 2.1 构建参数定义 (Parameters) ---
                // 这里简单定义一个通用的 input 参数
                // 实际上你可以解析 server.getArgs() 或 server.getToolEnabled() 来生成更精确的 JSON Schema
                ObjectNode paramsNode = objectMapper.createObjectNode();
                paramsNode.put("type", "object");

                ObjectNode propertiesNode = objectMapper.createObjectNode();
                ObjectNode inputProp = objectMapper.createObjectNode();
                inputProp.put("type", "string");
                inputProp.put("description", "The input data or command for the tool: " + server.getDescription());
                propertiesNode.set("input", inputProp);

                ArrayNode requiredNode = objectMapper.createArrayNode();
                requiredNode.add("input");

                paramsNode.set("properties", propertiesNode);
                paramsNode.set("required", requiredNode);

                // --- 2.2 创建工具定义 ---
                // 注意：这里传入了 MCPExecutor，告诉系统如何执行这个工具
                ToolDefinition mcpTool = new ToolDefinition(
                        server.getName(),
                        server.getDescription(),
                        paramsNode,
                        new McpExecutor(server, objectMapper),
                        "MCP" // MCP 工具类型
                );
                allTools.add(mcpTool);
            }
        } catch (Exception e) {
            log.error("加载 MCP 工具列表失败", e);
        }

        // 添加 YAML 工具
        for (YamlToolDefinition yamlTool : yamlToolLoader.getEnabledTools()) {
            // 跳过已存在的内置工具
            if (builtinTools.containsKey(yamlTool.getName())) {
                continue;
            }

            try {
                JsonNode params = objectMapper.valueToTree(yamlTool.toFunctionParameters());
                allTools.add(new ToolDefinition(
                        yamlTool.getName(),
                        yamlTool.getShortDescription() != null ? yamlTool.getShortDescription()
                                : yamlTool.getDescription(),
                        params,
                        null,
                        "MCP"
                ));
            } catch (Exception e) {
                log.error("转换 YAML 工具失败: {}", yamlTool.getName(), e);
            }
        }



        return allTools;
    }

    /**
     * 执行工具
     */
    public String execute(String name, String argumentsJson) {
        // 优先检查内置工具
        ToolDefinition builtin = builtinTools.get(name);
        if (builtin != null && builtin.executor != null) {
            try {
                JsonNode args = objectMapper.readTree(argumentsJson);
                log.info("执行内置工具: {} 参数: {}", name, argumentsJson);
                return builtin.executor.apply(args);
            } catch (Exception e) {
                log.error("执行内置工具失败: " + name, e);
                return "错误: " + e.getMessage();
            }
        }

        // 尝试 YAML 工具
        YamlToolDefinition yamlTool = yamlToolLoader.getTool(name);
        if (yamlTool != null) {
            log.info("执行 YAML 工具: {} 参数: {}", name, argumentsJson);
            return yamlToolLoader.execute(name, argumentsJson);
        }

        return "错误: 工具不存在: " + name;
    }

    /**
     * 获取工具
     */
    public ToolDefinition getTool(String name) {
        ToolDefinition builtin = builtinTools.get(name);
        if (builtin != null) {
            return builtin;
        }

        YamlToolDefinition yamlTool = yamlToolLoader.getTool(name);
        if (yamlTool != null) {
            try {
                JsonNode params = objectMapper.valueToTree(yamlTool.toFunctionParameters());
                return new ToolDefinition(
                        yamlTool.getName(),
                        yamlTool.getShortDescription(),
                        params,
                        null);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /**
     * 刷新 YAML 工具
     */
    public void refresh() {
        yamlToolLoader.refresh();
        log.info("工具刷新完成: {} 个 YAML 工具", yamlToolLoader.getAllTools().size());
    }

    /**
     * 执行系统命令
     */
    private String executeCommand(String command) {
        log.info("执行命令: {}", command);
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.warn("命令退出码 {}: {}", exitCode, command);
                }
                return output.toString();
            }
        } catch (Exception e) {
            log.error("命令执行失败: {}", command, e);
            return "错误: " + e.getMessage();
        }
    }

    public void registerBuiltinTool(String name, String description, String parametersJson,
            Function<JsonNode, String> executor) {
        registerBuiltinTool(name, description, parametersJson, executor, "MCP");
    }

    /**
     * 注册内置工具（带 toolType）
     */
    public void registerBuiltinTool(String name, String description, String parametersJson,
            Function<JsonNode, String> executor, String toolType) {
        try {
            JsonNode params = objectMapper.readTree(parametersJson);
            builtinTools.put(name, new ToolDefinition(name, description, params, executor, toolType));
        } catch (JsonProcessingException e) {
            log.error("解析工具参数失败: " + name, e);
        }
    }

    public record ToolDefinition(String name, String description, JsonNode parameters,
            Function<JsonNode, String> executor, String toolType) {
        
        public ToolDefinition(String name, String description, JsonNode parameters,
                Function<JsonNode, String> executor) {
            this(name, description, parameters, executor, "MCP");
        }
    }
}
