package com.example.rag.web.dto;

/**
 * 一条向量检索结果。
 */
public record SearchHit(
        String chunkId,
        Long docId,
        String title,
        Integer chunkIndex,
        double score,
        String text) {
}
