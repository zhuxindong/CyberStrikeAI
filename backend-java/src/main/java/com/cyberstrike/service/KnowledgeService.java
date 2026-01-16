package com.cyberstrike.service;

import com.cyberstrike.entity.KnowledgeItem;
import com.cyberstrike.repository.KnowledgeItemRepository;
import com.cyberstrike.service.openai.OpenAiService;
import com.cyberstrike.service.openai.model.OpenAIModels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgeItemRepository repository;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory vector cache: ItemID -> Embedding Vector
    private final Map<String, List<Double>> embeddingCache = new ConcurrentHashMap<>();

    public KnowledgeService(KnowledgeItemRepository repository, OpenAiService openAiService) {
        this.repository = repository;
        this.openAiService = openAiService;
    }

    /**
     * Load embeddings into memory on startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        loadCache();
    }

    public synchronized void loadCache() {
        embeddingCache.clear();
        List<KnowledgeItem> items = repository.findAll();
        int count = 0;
        for (KnowledgeItem item : items) {
            if (item.getEmbedding() != null && !item.getEmbedding().isEmpty()) {
                try {
                    List<Double> vector = objectMapper.readValue(item.getEmbedding(), List.class);
                    embeddingCache.put(item.getId(), vector);
                    count++;
                } catch (Exception e) {
                    // ignore invalid format
                }
            }
        }
        System.out.println("Loaded " + count + " knowledge embeddings into cache.");
    }

    /**
     * Search for similar items
     */
    public List<KnowledgeItem> search(String query, int topK) {
        if (embeddingCache.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Generate query embedding
        List<Double> queryVector = getEmbedding(query);
        if (queryVector == null) {
            return Collections.emptyList();
        }

        // 2. Calculate similarity
        PriorityQueue<Map.Entry<String, Double>> pq = new PriorityQueue<>(
                Map.Entry.comparingByValue()); // Min-heap, retrieve max elements

        for (Map.Entry<String, List<Double>> entry : embeddingCache.entrySet()) {
            double similarity = cosineSimilarity(queryVector, entry.getValue());
            if (pq.size() < topK) {
                pq.offer(new AbstractMap.SimpleEntry<>(entry.getKey(), similarity));
            } else if (similarity > pq.peek().getValue()) {
                pq.poll();
                pq.offer(new AbstractMap.SimpleEntry<>(entry.getKey(), similarity));
            }
        }

        List<String> topIds = new ArrayList<>();
        while (!pq.isEmpty()) {
            topIds.add(pq.poll().getKey());
        }
        Collections.reverse(topIds); // Highest similarity first

        if (topIds.isEmpty()) {
            return Collections.emptyList();
        }

        return repository.findAllById(topIds).stream()
                .sorted(Comparator.comparingInt(item -> topIds.indexOf(item.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Rebuild index: Generate embeddings for all items
     */
    public void rebuildIndex() {
        List<KnowledgeItem> items = repository.findAll();
        for (KnowledgeItem item : items) {
            // Construct text representation
            String text = "Title: " + item.getTitle() + "\nCategory: " + item.getCategory() + "\nContent: "
                    + item.getContent();

            // Generate embedding
            List<Double> vector = getEmbedding(text);
            if (vector != null) {
                try {
                    item.setEmbedding(objectMapper.writeValueAsString(vector));
                    repository.save(item);
                    embeddingCache.put(item.getId(), vector);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private List<Double> getEmbedding(String text) {
        try {
            OpenAIModels.EmbeddingRequest request = new OpenAIModels.EmbeddingRequest("text-embedding-3-small", text);
            OpenAIModels.EmbeddingResponse response = openAiService.createEmbeddings(request);
            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                return response.getData().get(0).getEmbedding();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.size() != v2.size())
            return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            // Safe conversion in case of mixed types in List
            double val1 = ((Number) v1.get(i)).doubleValue();
            double val2 = ((Number) v2.get(i)).doubleValue();

            dotProduct += val1 * val2;
            normA += Math.pow(val1, 2);
            normB += Math.pow(val2, 2);
        }
        if (normA == 0 || normB == 0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
