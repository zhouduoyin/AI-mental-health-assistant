package com.example.rag.service;

import com.example.rag.domain.Article;
import com.example.rag.domain.UserAccount;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.BatchItemResult;
import com.example.rag.web.dto.BatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 管理端批量操作。
 *
 * <p>刻意采用“逐项执行 + 汇总结果”的语义：一个 ID 不存在或受保护，不应该让其它
 * 合法项全部失败；前端根据 failed/items 展示哪些项需要重试。用户和文章都在这里
 * 复用原有单条服务，因此停用自己、删除最后一个管理员等保护仍然生效。</p>
 *
 * <p>只对用户和文章提供批量操作：情绪日志属于敏感数据，咨询记录需要逐条判断，
 * 不提供一键批量删除；知识库在鉴权修复前也不扩大批量删除面。</p>
 */
@Service
public class AdminBatchService {

    private static final Logger log = LoggerFactory.getLogger(AdminBatchService.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final UserService userService;
    private final ArticleService articleService;

    public AdminBatchService(UserService userService, ArticleService articleService) {
        this.userService = userService;
        this.articleService = articleService;
    }

    public BatchResult changeUserStatus(List<Long> ids, boolean enabled, UserAccount operator) {
        return run(ids, id -> userService.changeStatus(id, enabled, operator));
    }

    public BatchResult deleteUsers(List<Long> ids, UserAccount operator) {
        return run(ids, id -> userService.delete(id, operator));
    }

    public BatchResult changeArticleStatus(List<Long> ids, String status) {
        String normalized = normalizeArticleStatus(status);
        return run(ids, id -> articleService.changeStatus(id, normalized));
    }

    public BatchResult deleteArticles(List<Long> ids) {
        return run(ids, articleService::delete);
    }

    private BatchResult run(List<Long> rawIds, BatchAction action) {
        List<Long> ids = rawIds == null
                ? List.of()
                : rawIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多操作 " + MAX_BATCH_SIZE + " 项");
        }

        List<BatchItemResult> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            try {
                action.run(id);
                items.add(BatchItemResult.ok(id));
            } catch (ResourceNotFoundException | IllegalArgumentException ex) {
                log.warn("批量操作单项失败，id={}：{}", id, ex.getMessage());
                items.add(BatchItemResult.failed(id, ex.getMessage()));
            }
        }
        return BatchResult.of(items);
    }

    private String normalizeArticleStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (!Article.STATUS_PUBLISHED.equals(value) && !Article.STATUS_DRAFT.equals(value)) {
            throw new IllegalArgumentException("文章状态必须为：已发布 / 草稿");
        }
        return value;
    }

    @FunctionalInterface
    private interface BatchAction {
        void run(Long id);
    }
}
