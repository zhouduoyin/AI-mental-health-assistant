package com.example.rag.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台咨询记录。id 使用 C-1024 这样的业务编号，消息列表为对话摘录。
 */
@Entity
@Table(name = "consultation")
public class Consultation {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 64)
    private String userName;

    @Column(nullable = false, length = 8)
    private String initials;

    @Column(nullable = false, length = 16)
    private String tone;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private int duration;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 16)
    private String risk;

    @Column(nullable = false, length = 16)
    private String mood;

    /** 完整消息数；消息列表只保存对话摘录。 */
    @Column(nullable = false)
    private int messageCount;

    @OneToMany(mappedBy = "consultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<ConsultationMessage> messages = new ArrayList<>();

    protected Consultation() {
    }

    public Consultation(String id, String userName, String initials, String tone,
                        LocalDateTime startedAt, int duration, String topic, String status,
                        String risk, String mood, int messageCount) {
        this.id = id;
        this.userName = userName;
        this.initials = initials;
        this.tone = tone;
        this.startedAt = startedAt;
        this.duration = duration;
        this.topic = topic;
        this.status = status;
        this.risk = risk;
        this.mood = mood;
        this.messageCount = messageCount;
    }

    public void addMessage(String role, String text, String time) {
        messages.add(new ConsultationMessage(this, role, text, time));
    }

    public String getId() {
        return id;
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public List<ConsultationMessage> getMessages() {
        return messages;
    }
}
