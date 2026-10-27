package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.domain.KnowledgeDocument;
import com.example.rag.repository.KnowledgeDocumentRepository;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.DocumentDetail;
import com.example.rag.web.dto.DocumentView;
import com.example.rag.web.dto.IngestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文档入库/删除：原文写 MySQL，切块后写 Milvus。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_TEXT_CHARS = 1_000_000;

    private final KnowledgeDocumentRepository repository;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public DocumentService(KnowledgeDocumentRepository repository, VectorStore vectorStore, RagProperties properties) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.minChunkLengthToEmbed())
                .withMaxNumChunks(properties.maxNumChunks())
                .withKeepSeparator(true)
                .build();
    }

    /**
     * 文本入库：保存原文 → 切块 → 生成向量 → 写向量库。
     */
    @Transactional
    public DocumentView ingest(IngestRequest request) {
        return ingest(request.title(), request.source(), request.content());
    }

    @Transactional
    public DocumentView ingest(String title, String source, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (content.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("文档内容不能超过 " + MAX_TEXT_CHARS + " 字符");
        }

        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setTitle(title == null || title.isBlank() ? "未命名文档" : title.trim());
        entity.setSource(source);
        entity.setContent(content);
        entity.setChunkCount(0);
        entity = repository.saveAndFlush(entity);
        Long docId = entity.getId();

        List<Document> toStore = buildChunkDocuments(docId, entity.getTitle(), source, content);
        List<String> vectorIds = toStore.stream().map(Document::getId).toList();

        vectorStore.add(toStore);

        entity.setVectorIds(String.join(",", vectorIds));
        entity.setChunkCount(vectorIds.size());
        repository.save(entity);
        log.info("文档入库完成 docId={} title={} chunks={}", docId, entity.getTitle(), vectorIds.size());
        return toView(entity);
    }

    /**
     * 重建内存向量索引：MySQL 里的文档还在、向量库（SimpleVectorStore）重启后清空时使用。
     *
     * @return 重建的 chunk 数量
     */
    @Transactional
    public int rebuildIndex() {
        List<KnowledgeDocument> documents = repository.findAllByOrderByIdDesc();
        int total = 0;
        for (KnowledgeDocument document : documents) {
            List<Document> toStore = buildChunkDocuments(document.getId(), document.getTitle(),
                    document.getSource(), document.getContent());
            vectorStore.add(toStore);
            List<String> vectorIds = toStore.stream().map(Document::getId).toList();
            document.setVectorIds(String.join(",", vectorIds));
            document.setChunkCount(vectorIds.size());
            repository.save(document);
            total += vectorIds.size();
        }
        log.info("向量索引重建完成 documents={} chunks={}", documents.size(), total);
        return total;
    }

    private List<Document> buildChunkDocuments(Long docId, String title, String source, String content) {
        List<Document> chunks = splitter.split(new Document(content));
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文档切分结果为空，请检查内容");
        }
        List<Document> toStore = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String vectorId = docId + ":" + i;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("docId", docId);
            metadata.put("title", title);
            metadata.put("source", source == null ? "" : source);
            metadata.put("chunkIndex", i);
            toStore.add(Document.builder()
                    .id(vectorId)
                    .text(chunks.get(i).getText())
                    .metadata(metadata)
                    .build());
        }
        return toStore;
    }

    /**
     * 上传纯文本文件（.txt / .md）入库。
     */
    public DocumentView ingestFile(MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        boolean supported = lower.endsWith(".txt") || lower.endsWith(".md")
                || lower.endsWith(".markdown") || lower.endsWith(".text");
        if (!supported) {
            throw new IllegalArgumentException("仅支持 .txt / .md / .markdown 纯文本文件");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取上传文件失败", ex);
        }
        return ingest(title == null || title.isBlank() ? filename : title, filename, content);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list() {
        return repository.findAllByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(Long id) {
        KnowledgeDocument document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在: " + id));
        return new DocumentDetail(document.getId(), document.getTitle(), document.getSource(),
                document.getContent(), document.getChunkCount(), document.getCreatedAt());
    }

    /**
     * 删除文档：先删向量再删 MySQL 记录（向量 id 由 MySQL 记录提供）。
     */
    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在: " + id));
        List<String> vectorIds = parseVectorIds(document.getVectorIds());
        if (!vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }
        repository.delete(document);
        log.info("文档已删除 docId={} vectors={}", id, vectorIds.size());
    }

    private List<String> parseVectorIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private DocumentView toView(KnowledgeDocument document) {
        return new DocumentView(document.getId(), document.getTitle(), document.getSource(),
                document.getChunkCount(), document.getCreatedAt());
    }

    /**
     * 供 demo 初始化数据使用。
     */
    public boolean isEmpty() {
        return repository.count() == 0;
    }
}
