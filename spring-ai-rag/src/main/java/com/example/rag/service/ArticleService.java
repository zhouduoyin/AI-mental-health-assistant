package com.example.rag.service;

import com.example.rag.domain.Article;
import com.example.rag.repository.ArticleRepository;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.ArticleRequest;
import com.example.rag.web.dto.ArticleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ArticleService {

    private static final Set<String> CATEGORIES = Set.of("焦虑", "睡眠", "关系", "自我成长");
    private static final Set<String> COVERS = Set.of("teal", "lilac", "sand");
    private static final Set<String> STATUSES = Set.of(Article.STATUS_PUBLISHED, Article.STATUS_DRAFT);

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticleView> published(String category, String keyword, String sort) {
        List<Article> articles = articleRepository.findByStatusOrderByPublishedAtDesc(Article.STATUS_PUBLISHED);
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        Comparator<Article> comparator = switch (sort == null ? "latest" : sort) {
            case "popular" -> Comparator.comparingLong(Article::getViews).reversed();
            case "shortest" -> Comparator.comparingInt(Article::getMinutes);
            default -> Comparator
                    .comparing(Article::getPublishedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Article::getId, Comparator.reverseOrder());
        };
        return articles.stream()
                .filter(article -> normalizedCategory.isEmpty()
                        || normalizedCategory.equals(article.getCategory()))
                .filter(article -> normalizedKeyword.isEmpty()
                        || article.getTitle().toLowerCase().contains(normalizedKeyword)
                        || article.getSummary().toLowerCase().contains(normalizedKeyword)
                        || article.getAuthor().toLowerCase().contains(normalizedKeyword))
                .sorted(comparator)
                .map(ArticleView::from)
                .toList();
    }

    @Transactional
    public ArticleView readPublished(Long id) {
        Article article = require(id);
        if (!Article.STATUS_PUBLISHED.equals(article.getStatus())) {
            throw new ResourceNotFoundException("文章不存在或已下线：" + id);
        }
        article.setViews(article.getViews() + 1);
        return ArticleView.from(article);
    }

    @Transactional(readOnly = true)
    public List<ArticleView> all() {
        return articleRepository.findAll().stream()
                .sorted(Comparator.comparing(Article::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Article::getId, Comparator.reverseOrder()))
                .map(ArticleView::from)
                .toList();
    }

    @Transactional
    public ArticleView create(ArticleRequest request) {
        String status = normalizeStatus(request.status());
        String cover = normalizeCover(request.cover(), request.category());
        Article article = new Article(
                request.title().trim(),
                normalizeCategory(request.category()),
                normalizeAuthor(request.author()),
                normalizeSummary(request.summary(), request.content()),
                joinContent(request.content()),
                estimateMinutes(request.content()),
                0L,
                status,
                cover,
                Article.STATUS_PUBLISHED.equals(status) ? LocalDate.now() : null,
                LocalDate.now()
        );
        return ArticleView.from(articleRepository.save(article));
    }

    @Transactional
    public ArticleView update(Long id, ArticleRequest request) {
        Article article = require(id);
        String status = normalizeStatus(request.status());
        article.setTitle(request.title().trim());
        article.setCategory(normalizeCategory(request.category()));
        article.setAuthor(normalizeAuthor(request.author()));
        article.setSummary(normalizeSummary(request.summary(), request.content()));
        article.setContent(joinContent(request.content()));
        article.setMinutes(estimateMinutes(request.content()));
        article.setCover(normalizeCover(request.cover(), request.category()));
        article.setStatus(status);
        if (Article.STATUS_PUBLISHED.equals(status) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDate.now());
        }
        article.setUpdatedAt(LocalDate.now());
        return ArticleView.from(articleRepository.save(article));
    }

    @Transactional
    public ArticleView changeStatus(Long id, String status) {
        Article article = require(id);
        String normalized = normalizeStatus(status);
        article.setStatus(normalized);
        if (Article.STATUS_PUBLISHED.equals(normalized) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDate.now());
        }
        article.setUpdatedAt(LocalDate.now());
        return ArticleView.from(articleRepository.save(article));
    }

    @Transactional
    public void delete(Long id) {
        Article article = require(id);
        articleRepository.delete(article);
    }

    private Article require(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在：" + id));
    }

    private String normalizeCategory(String category) {
        String value = category == null ? "" : category.trim();
        if (!CATEGORIES.contains(value)) {
            throw new IllegalArgumentException("文章分类必须是：" + String.join(" / ", CATEGORIES));
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (!STATUSES.contains(value)) {
            throw new IllegalArgumentException("文章状态必须为：已发布 / 草稿");
        }
        return value;
    }

    private String normalizeCover(String cover, String category) {
        String value = cover == null ? "" : cover.trim();
        if (COVERS.contains(value)) {
            return value;
        }
        return switch (category == null ? "" : category) {
            case "睡眠" -> "lilac";
            case "关系" -> "sand";
            default -> "teal";
        };
    }

    private String normalizeAuthor(String author) {
        String value = author == null ? "" : author.trim();
        return value.isEmpty() ? "林医生" : value;
    }

    private String normalizeSummary(String summary, List<String> content) {
        String value = summary == null ? "" : summary.trim();
        if (!value.isEmpty()) {
            return value.length() > 500 ? value.substring(0, 500) : value;
        }
        String first = content == null || content.isEmpty() ? "" : content.get(0).trim();
        return first.length() > 120 ? first.substring(0, 120) + "…" : first;
    }

    private String joinContent(List<String> content) {
        return content.stream()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private int estimateMinutes(List<String> content) {
        int chars = content.stream().mapToInt(text -> text.trim().length()).sum();
        return Math.max(2, (int) Math.ceil(chars / 280.0));
    }
}
