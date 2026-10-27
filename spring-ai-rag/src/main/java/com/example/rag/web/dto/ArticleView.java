package com.example.rag.web.dto;

import com.example.rag.domain.Article;

import java.util.Arrays;
import java.util.List;

public record ArticleView(
        Long id,
        String title,
        String category,
        String author,
        String summary,
        int minutes,
        long views,
        String status,
        String publishedAt,
        String updated,
        String cover,
        List<String> content
) {

    public static ArticleView from(Article article) {
        String body = article.getContent() == null ? "" : article.getContent();
        List<String> paragraphs = Arrays.stream(body.split("\\R\\s*\\R"))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
        return new ArticleView(
                article.getId(),
                article.getTitle(),
                article.getCategory(),
                article.getAuthor(),
                article.getSummary(),
                article.getMinutes(),
                article.getViews(),
                article.getStatus(),
                article.getPublishedAt() == null ? "" : article.getPublishedAt().toString(),
                article.getUpdatedAt() == null ? "" : article.getUpdatedAt().toString(),
                article.getCover(),
                paragraphs
        );
    }
}
