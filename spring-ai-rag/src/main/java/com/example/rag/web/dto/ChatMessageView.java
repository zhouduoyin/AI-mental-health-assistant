package com.example.rag.web.dto;

import java.time.Instant;

/**
 * 历史消息。
 */
public record ChatMessageView(
        String role,
        String content,
        Instant createdAt) {
}
