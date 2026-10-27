package com.example.rag.web.dto;

import java.time.Instant;

/**
 * 文档列表项。
 */
public record DocumentView(
        Long id,
        String title,
        String source,
        int chunkCount,
        Instant createdAt) {
}
