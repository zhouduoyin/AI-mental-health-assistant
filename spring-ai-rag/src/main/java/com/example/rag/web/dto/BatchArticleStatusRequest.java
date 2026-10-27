package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量上线 / 下线文章。状态只允许“已发布”或“草稿”。
 */
public record BatchArticleStatusRequest(
        @NotEmpty(message = "请选择至少一篇文章")
        @Size(max = 100, message = "单次最多操作 100 篇文章")
        List<@NotNull(message = "文章 ID 不能为空") Long> ids,

        @NotBlank(message = "状态不能为空")
        String status
) {
}
