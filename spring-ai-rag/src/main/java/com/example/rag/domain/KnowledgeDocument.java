package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 知识库文档（MySQL 表 knowledge_document）。
 *
 * <p>原文存 MySQL，切块后的向量存 Milvus，两边通过 vectorIds 关联，删除文档时可精确清理向量。</p>
 */
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 该文档所有向量块 id，逗号分隔，例如 "12:0,12:1"。 */
    @Column(columnDefinition = "TEXT")
    private String vectorIds;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getVectorIds() {
        return vectorIds;
    }

    public void setVectorIds(String vectorIds) {
        this.vectorIds = vectorIds;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
