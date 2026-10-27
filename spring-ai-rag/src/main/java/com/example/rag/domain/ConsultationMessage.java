package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 咨询记录里的对话摘录。
 */
@Entity
@Table(name = "consultation_message")
public class ConsultationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false, length = 8)
    private String timeLabel;

    protected ConsultationMessage() {
    }

    ConsultationMessage(Consultation consultation, String role, String text, String timeLabel) {
        this.consultation = consultation;
        this.role = role;
        this.text = text;
        this.timeLabel = timeLabel;
    }

    public Long getId() {
        return id;
    }

    public Consultation getConsultation() {
        return consultation;
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public String getTimeLabel() {
        return timeLabel;
    }
}
