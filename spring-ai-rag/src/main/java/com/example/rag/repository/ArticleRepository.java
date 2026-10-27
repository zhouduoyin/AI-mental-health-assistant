package com.example.rag.repository;

import com.example.rag.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByStatusOrderByPublishedAtDesc(String status);
}
