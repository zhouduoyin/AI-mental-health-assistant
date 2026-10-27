package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ArticleStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
