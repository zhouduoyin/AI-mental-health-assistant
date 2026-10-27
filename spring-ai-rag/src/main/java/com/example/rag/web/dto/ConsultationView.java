package com.example.rag.web.dto;

import com.example.rag.domain.Consultation;
import com.example.rag.domain.ConsultationMessage;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record ConsultationView(
        String id,
        String user,
        String initials,
        String tone,
        String startedAt,
        int duration,
        String topic,
        String status,
        String risk,
        String mood,
        int messageCount,
        List<Message> messages
) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record Message(String role, String text, String time) {
    }

    public static ConsultationView from(Consultation consultation) {
        List<Message> messages = consultation.getMessages().stream()
                .map(ConsultationView::toMessage)
                .toList();
        return new ConsultationView(
                consultation.getId(),
                consultation.getUserName(),
                consultation.getInitials(),
                consultation.getTone(),
                consultation.getStartedAt().format(TIME_FORMAT),
                consultation.getDuration(),
                consultation.getTopic(),
                consultation.getStatus(),
                consultation.getRisk(),
                consultation.getMood(),
                consultation.getMessageCount(),
                messages
        );
    }

    private static Message toMessage(ConsultationMessage message) {
        return new Message(message.getRole(), message.getText(), message.getTimeLabel());
    }
}
