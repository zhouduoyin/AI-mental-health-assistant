package com.example.rag.web.dto;

import java.time.Instant;

/**
 * 文档详情（含原文）。
 */
public record DocumentDetail(
        Long id,
        String title,
        String source,
        String content,
        int chunkCount,
        Instant createdAt) {
}
