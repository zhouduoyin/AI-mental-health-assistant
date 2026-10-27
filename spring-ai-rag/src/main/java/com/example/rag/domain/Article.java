package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 前台心理科普文章，后台可创建、编辑、上下线。
 */
@Entity
@Table(name = "article")
public class Article {

    public static final String STATUS_PUBLISHED = "已发布";
    public static final String STATUS_DRAFT = "草稿";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 64)
    private String author;

    @Column(nullable = false, length = 500)
    private String summary;

    /** 段落之间用空行分隔。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int minutes;

    @Column(nullable = false)
    private long views;

    @Column(nullable = false, length = 16)
    private String status;

    /** teal / lilac / sand */
    @Column(nullable = false, length = 16)
    private String cover;

    private LocalDate publishedAt;

    @Column(nullable = false)
    private LocalDate updatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Article() {
    }

    public Article(String title, String category, String author, String summary, String content,
                   int minutes, long views, String status, String cover,
                   LocalDate publishedAt, LocalDate updatedAt) {
        this.title = title;
        this.category = category;
        this.author = author;
        this.summary = summary;
        this.content = content;
        this.minutes = minutes;
        this.views = views;
        this.status = status;
        this.cover = cover;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDate.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public long getViews() {
        return views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDate publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
