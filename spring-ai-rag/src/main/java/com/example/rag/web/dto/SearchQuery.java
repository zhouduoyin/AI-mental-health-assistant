package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 向量检索请求。
 *
 * @param topK                返回条数，缺省使用 rag.top-k
 * @param similarityThreshold 相似度阈值，缺省使用 rag.similarity-threshold
 */
public record SearchQuery(
        @NotBlank(message = "query 不能为空")
        String query,
        Integer topK,
        Double similarityThreshold) {
}
