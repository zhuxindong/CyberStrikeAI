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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 【Go -> Java 迁移】知识库检索服务
 * 对齐 Go 实现：internal/knowledge/retriever.go Retriever
 */
@Service
public class KnowledgeRetriever {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetriever.class);

    private final KnowledgeItemRepository itemRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${knowledge.retriever.top-k:5}")
    private int defaultTopK;

    @Value("${knowledge.retriever.similarity-threshold:0.7}")
    private double defaultSimilarityThreshold;

    @Value("${knowledge.retriever.hybrid-weight:0.7}")
    private double defaultHybridWeight;

    public KnowledgeRetriever(KnowledgeItemRepository itemRepository,
                             KnowledgeChunkRepository chunkRepository,
                             EmbeddingService embeddingService) {
        this.itemRepository = itemRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * 检索结果
     */
    public static class RetrievalResult {
        private KnowledgeChunk chunk;
        private KnowledgeItem item;
        private double similarity;
        private double score;

        public KnowledgeChunk getChunk() {
            return chunk;
        }

        public void setChunk(KnowledgeChunk chunk) {
            this.chunk = chunk;
        }

        public KnowledgeItem getItem() {
            return item;
        }

        public void setItem(KnowledgeItem item) {
            this.item = item;
        }

        public double getSimilarity() {
            return similarity;
        }

        public void setSimilarity(double similarity) {
            this.similarity = similarity;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }

    /**
     * 搜索请求
     */
    public static class SearchRequest {
        private String query;
        private String riskType;
        private int topK;
        private double threshold;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getRiskType() {
            return riskType;
        }

        public void setRiskType(String riskType) {
            this.riskType = riskType;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }
    }

    /**
     * 搜索知识库
     */
    public List<RetrievalResult> search(SearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isEmpty()) {
            throw new IllegalArgumentException("查询不能为空");
        }

        int topK = request.getTopK() > 0 ? request.getTopK() : defaultTopK;
        if (topK == 0) topK = 5;

        double threshold = request.getThreshold() > 0 ? request.getThreshold() : defaultSimilarityThreshold;
        if (threshold == 0) threshold = 0.7;

        // 向量化查询
        String queryText = request.getQuery();
        if (request.getRiskType() != null && !request.getRiskType().isEmpty()) {
            queryText = String.format("[风险类型: %s] %s", request.getRiskType(), request.getQuery());
        }

        List<Double> queryEmbedding = generateEmbedding(queryText);
        if (queryEmbedding == null) {
            logger.error("向量化查询失败");
            return Collections.emptyList();
        }

        // 查询所有块
        List<KnowledgeChunk> chunks;
        if (request.getRiskType() != null && !request.getRiskType().isEmpty()) {
            chunks = chunkRepository.findByCategory(request.getRiskType());
        } else {
            chunks = chunkRepository.findAll();
        }

        if (chunks.isEmpty()) {
            logger.info("没有找到知识块");
            return Collections.emptyList();
        }

        // 计算相似度
        List<Candidate> candidates = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty()) {
                continue;
            }

            List<Double> embedding = parseEmbedding(chunk.getEmbedding());
            if (embedding == null) {
                continue;
            }

            double similarity = cosineSimilarity(queryEmbedding, embedding);

            // 计算 BM25 分数
            double bm25Score = calculateBM25Score(request.getQuery(), chunk.getChunkText());

            // 获取关联的知识项
            Optional<KnowledgeItem> itemOpt = itemRepository.findById(chunk.getItemId());
            if (!itemOpt.isPresent()) {
                continue;
            }
            KnowledgeItem item = itemOpt.get();

            // 计算关键词匹配分数
            double categoryBM25 = calculateBM25Score(request.getQuery(), item.getCategory());
            double titleBM25 = calculateBM25Score(request.getQuery(), item.getTitle());
            double maxBM25 = Math.max(Math.max(bm25Score, categoryBM25), titleBM25);

            boolean hasStrongKeywordMatch = categoryBM25 > 0.3 || titleBM25 > 0.3;

            // 过滤极低相似度的结果
            if (similarity < 0.1) {
                continue;
            }

            Candidate candidate = new Candidate();
            candidate.setChunk(chunk);
            candidate.setItem(item);
            candidate.setSimilarity(similarity);
            candidate.setBm25Score(maxBM25);
            candidate.setHasStrongKeywordMatch(hasStrongKeywordMatch);
            candidates.add(candidate);
        }

        // 按相似度排序
        candidates.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        // 智能过滤
        List<Candidate> filteredCandidates = filterCandidates(candidates, threshold, topK);

        // 混合排序
        List<Candidate> finalCandidates = hybridSort(filteredCandidates, topK);

        // 转换为结果
        List<RetrievalResult> results = new ArrayList<>();
        for (Candidate candidate : finalCandidates) {
            RetrievalResult result = new RetrievalResult();
            result.setChunk(candidate.getChunk());
            result.setItem(candidate.getItem());
            result.setSimilarity(candidate.getSimilarity());
            result.setScore(candidate.getHybridScore());
            results.add(result);
        }

        // 上下文扩展
        return expandContext(results);
    }

    /**
     * 候选结果
     */
    private static class Candidate {
        private KnowledgeChunk chunk;
        private KnowledgeItem item;
        private double similarity;
        private double bm25Score;
        private boolean hasStrongKeywordMatch;
        private double hybridScore;

        public KnowledgeChunk getChunk() {
            return chunk;
        }

        public void setChunk(KnowledgeChunk chunk) {
            this.chunk = chunk;
        }

        public KnowledgeItem getItem() {
            return item;
        }

        public void setItem(KnowledgeItem item) {
            this.item = item;
        }

        public double getSimilarity() {
            return similarity;
        }

        public void setSimilarity(double similarity) {
            this.similarity = similarity;
        }

        public double getBm25Score() {
            return bm25Score;
        }

        public void setBm25Score(double bm25Score) {
            this.bm25Score = bm25Score;
        }

        public boolean isHasStrongKeywordMatch() {
            return hasStrongKeywordMatch;
        }

        public void setHasStrongKeywordMatch(boolean hasStrongKeywordMatch) {
            this.hasStrongKeywordMatch = hasStrongKeywordMatch;
        }

        public double getHybridScore() {
            return hybridScore;
        }

        public void setHybridScore(double hybridScore) {
            this.hybridScore = hybridScore;
        }
    }

    /**
     * 智能过滤候选结果
     */
    private List<Candidate> filterCandidates(List<Candidate> candidates, double threshold, int topK) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 检查是否有任何关键词匹配
        boolean hasAnyKeywordMatch = candidates.stream().anyMatch(Candidate::isHasStrongKeywordMatch);

        // 检查最高相似度
        double maxSimilarity = candidates.get(0).getSimilarity();

        // 严格模式（阈值 >= 0.8）
        boolean strictMode = threshold >= 0.8;

        // 计算有效阈值
        double effectiveThreshold = threshold;
        if (!strictMode && !hasAnyKeywordMatch) {
            // 非严格模式下，没有关键词匹配，适度放宽阈值
            effectiveThreshold = Math.max(threshold * 0.85, 0.6);
        }

        List<Candidate> filtered = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.getSimilarity() >= effectiveThreshold) {
                filtered.add(candidate);
            } else if (!strictMode && candidate.isHasStrongKeywordMatch()) {
                // 非严格模式下，有关键词匹配但相似度略低于阈值
                double relaxedThreshold = Math.max(effectiveThreshold * 0.85, 0.55);
                if (candidate.getSimilarity() >= relaxedThreshold) {
                    filtered.add(candidate);
                }
            }
        }

        // 智能兜底策略
        if (filtered.isEmpty() && !strictMode && !candidates.isEmpty()) {
            double minAcceptableSimilarity = 0.55;
            if (maxSimilarity >= minAcceptableSimilarity) {
                int maxResults = Math.min(topK, candidates.size());
                for (Candidate candidate : candidates) {
                    if (candidate.getSimilarity() >= minAcceptableSimilarity && filtered.size() < maxResults) {
                        filtered.add(candidate);
                    }
                }
            }
        }

        // 限制 Top-K
        if (filtered.size() > topK) {
            filtered = filtered.subList(0, topK);
        }

        return filtered;
    }

    /**
     * 混合排序
     */
    private List<Candidate> hybridSort(List<Candidate> candidates, int topK) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        double hybridWeight = defaultHybridWeight;
        if (hybridWeight < 0 || hybridWeight > 1) {
            hybridWeight = 0.7;
        }

        // 计算混合分数
        for (Candidate candidate : candidates) {
            double normalizedBM25 = Math.min(candidate.getBm25Score(), 1.0);
            candidate.setHybridScore(hybridWeight * candidate.getSimilarity() + (1 - hybridWeight) * normalizedBM25);
        }

        // 按混合分数排序
        candidates.sort((a, b) -> Double.compare(b.getHybridScore(), a.getHybridScore()));

        // 限制 Top-K
        if (candidates.size() > topK) {
            return candidates.subList(0, topK);
        }

        return candidates;
    }

    /**
     * 扩展检索结果的上下文
     */
    private List<RetrievalResult> expandContext(List<RetrievalResult> results) {
        if (results.isEmpty()) {
            return results;
        }

        // 收集所有匹配到的文档ID
        Set<String> itemIds = results.stream()
                .map(r -> r.getItem().getId())
                .collect(Collectors.toSet());

        // 按文档分组结果
        Map<String, List<RetrievalResult>> resultsByItem = new HashMap<>();
        for (RetrievalResult result : results) {
            String itemId = result.getItem().getId();
            resultsByItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(result);
        }

        // 扩展每个文档的结果
        List<RetrievalResult> expandedResults = new ArrayList<>();
        Set<String> processedChunkIds = new HashSet<>();

        for (Map.Entry<String, List<RetrievalResult>> entry : resultsByItem.entrySet()) {
            String itemId = entry.getKey();
            List<RetrievalResult> itemResults = entry.getValue();

            // 获取该文档的所有块
            List<KnowledgeChunk> allChunks = chunkRepository.findByItemIdOrderByChunkIndex(itemId);
            if (allChunks.isEmpty()) {
                // 如果无法加载块，直接添加原始结果
                for (RetrievalResult result : itemResults) {
                    if (!processedChunkIds.contains(result.getChunk().getId())) {
                        expandedResults.add(result);
                        processedChunkIds.add(result.getChunk().getId());
                    }
                }
                continue;
            }

            // 添加原始结果
            for (RetrievalResult result : itemResults) {
                if (!processedChunkIds.contains(result.getChunk().getId())) {
                    expandedResults.add(result);
                    processedChunkIds.add(result.getChunk().getId());
                }
            }

            // 对混合分数最高的前3个匹配块进行扩展
            List<RetrievalResult> sortedItemResults = new ArrayList<>(itemResults);
            sortedItemResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

            int maxExpandFrom = Math.min(3, sortedItemResults.size());
            Set<Integer> expandedChunkIndices = new HashSet<>();

            for (int i = 0; i < maxExpandFrom; i++) {
                RetrievalResult mainResult = sortedItemResults.get(i);
                int mainChunkIndex = mainResult.getChunk().getChunkIndex();
                expandedChunkIndices.add(mainChunkIndex);

                // 添加相邻的块（前后各1个）
                for (int offset = -1; offset <= 1; offset++) {
                    if (offset == 0) continue;
                    int adjacentIndex = mainChunkIndex + offset;
                    if (adjacentIndex < 0 || adjacentIndex >= allChunks.size()) continue;
                    if (expandedChunkIndices.contains(adjacentIndex)) continue;

                    KnowledgeChunk adjacentChunk = allChunks.get(adjacentIndex);
                    if (!processedChunkIds.contains(adjacentChunk.getId())) {
                        RetrievalResult adjacentResult = new RetrievalResult();
                        adjacentResult.setChunk(adjacentChunk);
                        adjacentResult.setItem(mainResult.getItem());
                        adjacentResult.setSimilarity(mainResult.getSimilarity());
                        adjacentResult.setScore(mainResult.getScore() * 0.9); // 降低分数
                        expandedResults.add(adjacentResult);
                        processedChunkIds.add(adjacentChunk.getId());
                        expandedChunkIndices.add(adjacentIndex);
                    }
                }
            }
        }

        return expandedResults;
    }

    /**
     * 生成文本向量
     */
    private List<Double> generateEmbedding(String text) {
        return embeddingService.generateEmbedding(text);
    }

    /**
     * 解析向量
     */
    private List<Double> parseEmbedding(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, List.class);
        } catch (JsonProcessingException e) {
            logger.error("解析向量失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double val1 = a.get(i);
            double val2 = b.get(i);

            dotProduct += val1 * val2;
            normA += val1 * val1;
            normB += val2 * val2;
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 计算 BM25 分数
     */
    private double calculateBM25Score(String query, String text) {
        if (query == null || query.isEmpty() || text == null || text.isEmpty()) {
            return 0.0;
        }

        String[] queryTerms = query.toLowerCase().split("\\s+");
        String textLower = text.toLowerCase();
        String[] textTerms = textLower.split("\\s+");

        if (textTerms.length == 0) {
            return 0.0;
        }

        // BM25 参数
        double k1 = 1.2;
        double b = 0.75;
        double avgDocLength = 150.0;
        double docLength = textTerms.length;

        // 计算词频映射
        Map<String, Integer> textTermFreq = new HashMap<>();
        for (String term : textTerms) {
            textTermFreq.put(term, textTermFreq.getOrDefault(term, 0) + 1);
        }

        double score = 0.0;
        int matchedQueryTerms = 0;

        for (String term : queryTerms) {
            Integer termFreq = textTermFreq.get(term);
            if (termFreq == null || termFreq == 0) {
                continue;
            }
            matchedQueryTerms++;

            // BM25 TF 计算
            double tf = termFreq;
            double lengthNorm = 1 - b + b * (docLength / avgDocLength);
            double tfScore = tf / (tf + k1 * lengthNorm);

            // IDF 计算
            double idfWeight = 1.0;
            int termLen = term.length();
            if (termLen <= 2) {
                idfWeight = 1.2 + Math.log(1.0 + termFreq / 20.0);
            } else if (termLen <= 4) {
                idfWeight = 1.0 + Math.log(1.0 + termFreq / 15.0);
            } else {
                idfWeight = 0.9 + Math.log(1.0 + termFreq / 10.0);
            }

            score += tfScore * idfWeight;
        }

        // 归一化
        if (queryTerms.length > 0) {
            double matchRatio = (double) matchedQueryTerms / queryTerms.length;
            score = (score / queryTerms.length) * (1 + matchRatio) / 2;
        }

        return Math.min(score, 1.0);
    }
}
