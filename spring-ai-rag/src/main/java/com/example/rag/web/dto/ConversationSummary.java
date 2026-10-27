package com.example.rag.web.dto;

import java.time.Instant;

/**
 * 对话记录列表项。
 */
public record ConversationSummary(
        String id,
        String title,
        String preview,
        Instant updatedAt,
        int messageCount) {
}
