package com.example.rag.web;

import com.example.rag.service.ArticleService;
import com.example.rag.web.dto.ArticleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台文章接口：只暴露已发布内容。
 */
@RestController
@RequestMapping("/api/articles")
@Tag(name = "前台文章", description = "心理科普文章列表与详情")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    @Operation(summary = "已发布文章列表")
    public List<ArticleView> list(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String sort) {
        return articleService.published(category, keyword, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "文章详情（阅读量 +1）")
    public ArticleView get(@PathVariable Long id) {
        return articleService.readPublished(id);
    }
}
