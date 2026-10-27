package com.example.rag.web;

import com.example.rag.service.AdminBatchService;
import com.example.rag.service.ArticleService;
import com.example.rag.service.AuthTokenService;
import com.example.rag.web.dto.ArticleRequest;
import com.example.rag.web.dto.ArticleStatusRequest;
import com.example.rag.web.dto.ArticleView;
import com.example.rag.web.dto.BatchArticleStatusRequest;
import com.example.rag.web.dto.BatchIdsRequest;
import com.example.rag.web.dto.BatchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/articles")
@Tag(name = "后台文章管理", description = "文章增删改查与上下线，需要后台账号")
public class AdminArticleController {

    private final ArticleService articleService;
    private final AdminBatchService adminBatchService;
    private final AuthTokenService tokenService;

    public AdminArticleController(ArticleService articleService,
                                  AdminBatchService adminBatchService,
                                  AuthTokenService tokenService) {
        this.articleService = articleService;
        this.adminBatchService = adminBatchService;
        this.tokenService = tokenService;
    }

    @GetMapping
    @Operation(summary = "全部文章（含草稿）")
    public List<ArticleView> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        tokenService.requireBack(authorization);
        return articleService.all();
    }

    @PostMapping
    @Operation(summary = "新建文章")
    public ArticleView create(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @Valid @RequestBody ArticleRequest request) {
        tokenService.requireBack(authorization);
        return articleService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑文章")
    public ArticleView update(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @PathVariable Long id,
                              @Valid @RequestBody ArticleRequest request) {
        tokenService.requireBack(authorization);
        return articleService.update(id, request);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "修改文章状态（上线 / 下线）")
    public ArticleView changeStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ArticleStatusRequest request) {
        tokenService.requireBack(authorization);
        return articleService.changeStatus(id, request.status());
    }

    @PostMapping("/batch/status")
    @Operation(summary = "批量上线 / 下线文章",
            description = "逐项执行，单篇不存在不会回滚其它文章")
    public BatchResult batchStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody BatchArticleStatusRequest request) {
        tokenService.requireBack(authorization);
        return adminBatchService.changeArticleStatus(request.ids(), request.status());
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "批量删除文章",
            description = "逐项执行，删除后不可恢复")
    public BatchResult batchDelete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody BatchIdsRequest request) {
        tokenService.requireBack(authorization);
        return adminBatchService.deleteArticles(request.ids());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除文章")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        tokenService.requireBack(authorization);
        articleService.delete(id);
    }
}
