package com.example.rag.web;

import com.example.rag.service.VectorSearchService;
import com.example.rag.web.dto.SearchHit;
import com.example.rag.web.dto.SearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Tag(name = "向量检索", description = "直接调用向量库做相似度检索")
public class SearchController {

    private final VectorSearchService vectorSearchService;

    public SearchController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping
    @Operation(summary = "语义检索")
    public List<SearchHit> search(@Valid @RequestBody SearchQuery request) {
        return vectorSearchService.search(request.query(), request.topK(), request.similarityThreshold());
    }
}
