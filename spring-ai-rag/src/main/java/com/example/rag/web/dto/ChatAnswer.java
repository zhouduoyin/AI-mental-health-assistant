package com.example.rag.web.dto;

import java.util.List;

/**
 * 问答响应：模型回答 + 本次命中的参考资料。
 */
public record ChatAnswer(
        String conversationId,
        String answer,
        List<SearchHit> references) {
}
