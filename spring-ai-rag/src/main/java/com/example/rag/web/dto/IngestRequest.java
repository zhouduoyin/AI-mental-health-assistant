package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文本入库请求。
 */
public record IngestRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 200, message = "title 最多 200 字符")
        String title,

        @Size(max = 500, message = "source 最多 500 字符")
        String source,

        @NotBlank(message = "content 不能为空")
        String content) {
}
