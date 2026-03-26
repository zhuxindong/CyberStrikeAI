package com.cyberstrike.service;

import com.cyberstrike.entity.KnowledgeChunk;
import com.cyberstrike.entity.KnowledgeItem;
import com.cyberstrike.repository.KnowledgeChunkRepository;
import com.cyberstrike.repository.KnowledgeItemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 【Go -> Java 迁移】知识库索引服务
 * 对齐 Go 实现：internal/knowledge/indexer.go Indexer
 */
@Service
public class KnowledgeIndexer {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeItemRepository itemRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${knowledge.indexer.chunk-size:512}")
    private int chunkSize;

    @Value("${knowledge.indexer.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${knowledge.indexer.max-chunks-per-item:0}")
    private int maxChunksPerItem;

    // 错误跟踪
    private String lastError = null;
    private LocalDateTime lastErrorTime = null;
    private int errorCount = 0;

    // 重建索引状态跟踪
    private boolean isRebuilding = false;
    private int rebuildTotalItems = 0;
    private int rebuildCurrent = 0;
    private int rebuildFailed = 0;
    private LocalDateTime rebuildStartTime = null;
    private String rebuildLastItemId = null;
    private int rebuildLastChunks = 0;

    public KnowledgeIndexer(KnowledgeItemRepository itemRepository,
                           KnowledgeChunkRepository chunkRepository,
                           EmbeddingService embeddingService) {
        this.itemRepository = itemRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * 将文本分块（支持 Markdown 标题分割）
     */
    public List<String> chunkText(String text) {
        // 按 Markdown 标题分割，获取带标题的块
        List<Section> sections = splitByMarkdownHeadersWithContent(text);

        List<String> result = new ArrayList<>();
        for (Section section : sections) {
            // 构建父级标题路径
            String parentHeaderPath = "";
            if (section.getHeaderPath().size() > 1) {
                parentHeaderPath = String.join(" > ", section.getHeaderPath().subList(0, section.getHeaderPath().size() - 1));
            }

            // 提取第一行作为标题
            String[] lines = section.getContent().split("\n", 2);
            String firstLine = lines[0];
            String remainingContent = lines.length > 1 ? lines[1] : "";

            // 如果剩余内容为空，跳过
            if (remainingContent.trim().isEmpty()) {
                continue;
            }

            // 如果块大小合适，直接添加
            if (estimateTokens(section.getContent()) <= chunkSize) {
                if (!parentHeaderPath.isEmpty()) {
                    result.add(String.format("[%s] %s", parentHeaderPath, section.getContent()));
                } else {
                    result.add(section.getContent());
                }
            } else {
                // 块太大，按子标题或段落分割
                List<String> subSections = splitBySubHeaders(section.getContent());
                for (String sub : subSections) {
                    if (estimateTokens(sub) <= chunkSize) {
                        result.add(sub);
                    } else {
                        // 进一步按段落分割
                        List<String> paragraphs = splitByParagraphs(sub, firstLine, parentHeaderPath);
                        for (String para : paragraphs) {
                            if (estimateTokens(para) <= chunkSize) {
                                result.add(para);
                            } else {
                                // 按句子分割
                                List<String> sentenceChunks = splitBySentencesWithOverlap(para);
                                result.addAll(sentenceChunks);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 表示一个带标题路径的文本块
     */
    private static class Section {
        private List<String> headerPath = new ArrayList<>();
        private String content = "";

        public List<String> getHeaderPath() {
            return headerPath;
        }

        public void setHeaderPath(List<String> headerPath) {
            this.headerPath = headerPath;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 按 Markdown 标题分割，返回带标题路径的块
     */
    private List<Section> splitByMarkdownHeadersWithContent(String text) {
        Pattern headerPattern = Pattern.compile("(?m)^#{1,6}\\s+.+$");
        Matcher matcher = headerPattern.matcher(text);

        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end()});
        }

        if (matches.isEmpty()) {
            Section section = new Section();
            section.setHeaderPath(Collections.emptyList());
            section.setContent(text);
            return Collections.singletonList(section);
        }

        List<Section> sections = new ArrayList<>();
        List<String> currentHeaderPath = new ArrayList<>();

        for (int i = 0; i < matches.size(); i++) {
            int start = matches.get(i)[0];
            int end = matches.get(i)[1];
            int nextStart = i + 1 < matches.size() ? matches.get(i + 1)[0] : text.length();

            String headerLine = text.substring(start, end).trim();
            int level = 0;
            for (char c : headerLine.toCharArray()) {
                if (c == '#') level++;
                else break;
            }

            // 更新标题路径
            List<String> newPath = new ArrayList<>();
            for (String h : currentHeaderPath) {
                int hLevel = 0;
                for (char c : h.toCharArray()) {
                    if (c == '#') hLevel++;
                    else break;
                }
                if (hLevel < level) {
                    newPath.add(h);
                }
            }
            newPath.add(headerLine);
            currentHeaderPath = newPath;

            String content = text.substring(start, nextStart).trim();

            Section section = new Section();
            section.setHeaderPath(new ArrayList<>(currentHeaderPath));
            section.setContent(content);
            sections.add(section);
        }

        // 过滤空块
        return sections.stream()
                .filter(s -> !s.getContent().trim().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 按子标题分割内容
     */
    private List<String> splitBySubHeaders(String content) {
        Pattern subHeaderPattern = Pattern.compile("(?m)^#{2,6}\\s+.+$");
        Matcher matcher = subHeaderPattern.matcher(content);

        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end()});
        }

        if (matches.isEmpty()) {
            return Collections.singletonList(content);
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            int start = matches.get(i)[0];
            int nextStart = i + 1 < matches.size() ? matches.get(i + 1)[0] : content.length();
            result.add(content.substring(start, nextStart).trim());
        }

        return result;
    }

    /**
     * 按段落分割
     */
    private List<String> splitByParagraphs(String content, String firstLine, String parentPath) {
        String[] paragraphs = content.split("\n\n");
        List<String> result = new ArrayList<>();

        for (int i = 0; i < paragraphs.length; i++) {
            String trimmed = paragraphs[i].trim();
            if (trimmed.isEmpty()) continue;

            // 过滤掉只有标题的段落
            if (trimmed.equals(firstLine.trim())) continue;

            if (i == 0 && trimmed.contains(firstLine)) {
                if (!parentPath.isEmpty()) {
                    result.add(String.format("[%s] %s", parentPath, trimmed));
                } else {
                    result.add(trimmed);
                }
            } else {
                if (!parentPath.isEmpty()) {
                    result.add(String.format("[%s]\n%s\n%s", parentPath, firstLine, trimmed));
                } else {
                    result.add(firstLine + "\n" + trimmed);
                }
            }
        }

        return result;
    }

    /**
     * 按句子分割并应用重叠策略
     */
    private List<String> splitBySentencesWithOverlap(String text) {
        if (chunkOverlap <= 0) {
            return splitBySentencesSimple(text);
        }

        String[] sentences = text.split("[.!?\u3002\uff01\uff1f]+");
        List<String> result = new ArrayList<>();
        String currentChunk = "";

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            String testChunk = currentChunk.isEmpty() ? sentence : currentChunk + "\n" + sentence;

            if (estimateTokens(testChunk) > chunkSize && !currentChunk.isEmpty()) {
                result.add(currentChunk);
                String overlapText = extractLastTokens(currentChunk, chunkOverlap);
                currentChunk = overlapText.isEmpty() ? sentence : overlapText + "\n" + sentence;
            } else {
                currentChunk = testChunk;
            }
        }

        if (!currentChunk.isEmpty()) {
            result.add(currentChunk);
        }

        return result.stream().filter(s -> !s.trim().isEmpty()).collect(Collectors.toList());
    }

    /**
     * 简单按句子分割（无重叠）
     */
    private List<String> splitBySentencesSimple(String text) {
        String[] sentences = text.split("[.!?\u3002\uff01\uff1f]+");
        List<String> result = new ArrayList<>();
        String currentChunk = "";

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            String testChunk = currentChunk.isEmpty() ? sentence : currentChunk + "\n" + sentence;

            if (estimateTokens(testChunk) > chunkSize && !currentChunk.isEmpty()) {
                result.add(currentChunk);
                currentChunk = sentence;
            } else {
                currentChunk = testChunk;
            }
        }

        if (!currentChunk.isEmpty()) {
            result.add(currentChunk);
        }

        return result;
    }

    /**
     * 从文本末尾提取指定 token 数量的内容
     */
    private String extractLastTokens(String text, int tokenCount) {
        if (tokenCount <= 0 || text.isEmpty()) return "";

        int charCount = tokenCount * 4;
        if (text.length() <= charCount) return text;

        String extracted = text.substring(text.length() - charCount);

        // 尝试找到第一个句子边界
        Pattern boundaryPattern = Pattern.compile("[.!?\u3002\uff01\uff1f]+");
        Matcher matcher = boundaryPattern.matcher(extracted);
        if (matcher.find() && matcher.start() > 0) {
            extracted = extracted.substring(matcher.start());
        }

        return extracted.trim();
    }

    /**
     * 估算 token 数（1 token ≈ 4 字符）
     */
    private int estimateTokens(String text) {
        return text.length() / 4;
    }

    /**
     * 索引单个知识项（分块并向量化）
     */
    @Transactional
    public void indexItem(String itemId) {
        Optional<KnowledgeItem> itemOpt = itemRepository.findById(itemId);
        if (!itemOpt.isPresent()) {
            logger.warn("知识项不存在: {}", itemId);
            return;
        }

        KnowledgeItem item = itemOpt.get();

        // 删除旧的块
        chunkRepository.deleteByItemId(itemId);

        // 分块
        List<String> chunks = chunkText(item.getContent());
        if (chunks.isEmpty()) {
            logger.info("知识项内容为空，跳过索引: {}", itemId);
            return;
        }

        // 应用最大块数限制
        if (maxChunksPerItem > 0 && chunks.size() > maxChunksPerItem) {
            logger.info("知识项块数量超过限制，已截断: {} (原始: {}, 限制: {})",
                    itemId, chunks.size(), maxChunksPerItem);
            chunks = chunks.subList(0, maxChunksPerItem);
        }

        // 为每个块生成向量
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            // 构建包含分类和标题的文本
            String embedText = String.format("[风险类型: %s] %s", item.getCategory(), chunkText);

            List<Double> embedding = generateEmbedding(embedText);
            if (embedding == null) {
                logger.warn("生成向量失败，跳过块 {} (itemId: {}, index: {})", chunkText.substring(0, Math.min(50, chunkText.length())), itemId, i);
                continue;
            }

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setItemId(itemId);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkText);
            try {
                chunk.setEmbedding(objectMapper.writeValueAsString(embedding));
            } catch (JsonProcessingException e) {
                logger.error("序列化向量失败", e);
                continue;
            }
            chunk.setCreatedAt(LocalDateTime.now());

            chunkRepository.save(chunk);
        }

        logger.info("知识项索引完成: {} ({} 个块)", itemId, chunks.size());
    }

    /**
     * 生成文本向量
     */
    private List<Double> generateEmbedding(String text) {
        return embeddingService.generateEmbedding(text);
    }


    /**
     * 重建所有知识项的索引
     */
    public synchronized void rebuildIndex() {
        if (isRebuilding) {
            logger.warn("索引重建正在进行中，跳过");
            return;
        }

        isRebuilding = true;
        rebuildStartTime = LocalDateTime.now();
        rebuildTotalItems = 0;
        rebuildCurrent = 0;
        rebuildFailed = 0;

        try {
            List<KnowledgeItem> items = itemRepository.findAll();
            rebuildTotalItems = items.size();
            logger.info("开始重建索引，共 {} 个知识项", rebuildTotalItems);

            for (KnowledgeItem item : items) {
                try {
                    indexItem(item.getId());
                    rebuildCurrent++;
                    rebuildLastItemId = item.getId();
                } catch (Exception e) {
                    rebuildFailed++;
                    logger.error("索引知识项失败: {}", item.getId(), e);
                }
            }

            logger.info("索引重建完成: 总计: {}, 成功: {}, 失败: {}",
                    rebuildTotalItems, rebuildCurrent, rebuildFailed);
        } finally {
            isRebuilding = false;
        }
    }

    /**
     * 获取索引状态
     */
    public Map<String, Object> getIndexStatus() {
        long totalItems = itemRepository.countAll();
        long totalChunks = chunkRepository.count();

        Map<String, Object> status = new HashMap<>();
        status.put("is_rebuilding", isRebuilding);
        status.put("total_items", totalItems);
        status.put("total_chunks", totalChunks);
        status.put("rebuild_total_items", rebuildTotalItems);
        status.put("rebuild_current", rebuildCurrent);
        status.put("rebuild_failed", rebuildFailed);
        status.put("rebuild_progress_percent", rebuildTotalItems > 0 ? (double) rebuildCurrent / rebuildTotalItems * 100 : 0);
        status.put("last_error", lastError);
        status.put("last_error_time", lastErrorTime);
        status.put("error_count", errorCount);

        return status;
    }

    /**
     * 检查是否存在索引
     * 对齐 Go：indexer.HasIndex
     */
    public boolean hasIndex() {
        // 检查是否有任何知识块被索引
        long chunkCount = chunkRepository.count();
        return chunkCount > 0;
    }

    /**
     * 是否正在重建索引
     */
    public boolean isRebuilding() {
        return isRebuilding;
    }
}
