package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 问答请求。conversationId 为空时自动新建会话。
 */
public record ChatAsk(
        String conversationId,
        @NotBlank(message = "question 不能为空")
        String question) {
}
