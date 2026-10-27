package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 一条每日情绪记录。前台按用户隔离，后台可以查看全部。
 */
@Entity
@Table(name = "mood_log", indexes = {
        @Index(name = "idx_mood_log_user_date", columnList = "user_id, mood_date"),
        @Index(name = "idx_mood_log_date", columnList = "mood_date")
})
public class MoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号 id；后台运营数据里的匿名来访者可以为空。 */
    private Long userId;

    @Column(nullable = false, length = 64)
    private String userName;

    @Column(nullable = false, length = 8)
    private String initials;

    /** teal / lilac / sand */
    @Column(nullable = false, length = 16)
    private String tone;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(name = "mood_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int score;

    /** 逗号分隔的标签。 */
    @Column(length = 500)
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MoodLog() {
    }

    public MoodLog(Long userId, String userName, String initials, String tone,
                   String sessionId, LocalDate date, int score, String tags, String note) {
        this.userId = userId;
        this.userName = userName;
        this.initials = initials;
        this.tone = tone;
        this.sessionId = sessionId;
        this.date = date;
        this.score = score;
        this.tags = tags;
        this.note = note;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
