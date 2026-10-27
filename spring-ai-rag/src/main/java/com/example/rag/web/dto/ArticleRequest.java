package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台新建 / 编辑文章的请求。正文按段落数组传入。
 */
public record ArticleRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题最多 200 字")
        String title,

        @NotBlank(message = "分类不能为空")
        String category,

        @Size(max = 500, message = "摘要最多 500 字")
        String summary,

        @NotEmpty(message = "正文至少需要一个段落")
        List<@NotBlank(message = "段落不能为空") String> content,

        @NotBlank(message = "状态不能为空")
        String status,

        String cover,

        String author
) {
}
