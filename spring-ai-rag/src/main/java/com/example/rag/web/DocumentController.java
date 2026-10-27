package com.example.rag.web;

import com.example.rag.service.DocumentService;
import com.example.rag.web.dto.DocumentDetail;
import com.example.rag.web.dto.DocumentView;
import com.example.rag.web.dto.IngestRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "文档管理", description = "文本/文件入库、查询、删除")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @Operation(summary = "文本入库", description = "保存原文到 MySQL，切块后向量化写入 Milvus")
    public DocumentView ingest(@Valid @RequestBody IngestRequest request) {
        return documentService.ingest(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传纯文本文件入库", description = "支持 .txt / .md / .markdown，UTF-8 编码")
    public DocumentView upload(@RequestPart("file") MultipartFile file,
                               @RequestParam(value = "title", required = false) String title) {
        return documentService.ingestFile(file, title);
    }

    @GetMapping
    @Operation(summary = "文档列表")
    public List<DocumentView> list() {
        return documentService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "文档详情（含原文）")
    public DocumentDetail get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除文档（同时删除 Milvus 中的向量）")
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }
}
