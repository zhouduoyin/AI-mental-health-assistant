package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.web.dto.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量检索：把向量库返回的 {@link Document} 转换成对外的 {@link SearchHit}。
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final VectorStore vectorStore;
    private final RagProperties properties;

    public VectorSearchService(VectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<SearchHit> search(String query, Integer topK, Double similarityThreshold) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索内容不能为空");
        }
        int k = topK == null ? properties.topK() : Math.max(1, Math.min(topK, 20));
        double threshold = similarityThreshold == null
                ? properties.similarityThreshold()
                : Math.max(0.0d, Math.min(similarityThreshold, 1.0d));

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(k)
                .similarityThreshold(threshold)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null) {
            return List.of();
        }
        log.debug("向量检索 query={} topK={} threshold={} hits={}", query, k, threshold, documents.size());
        return documents.stream().map(this::toHit).toList();
    }

    private SearchHit toHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Long docId = asLong(metadata.get("docId"));
        Integer chunkIndex = asInt(metadata.get("chunkIndex"));
        String title = metadata.get("title") == null ? "" : metadata.get("title").toString();
        double score = document.getScore() == null ? 0.0d : document.getScore();
        return new SearchHit(document.getId(), docId, title, chunkIndex, round(score), document.getText());
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
