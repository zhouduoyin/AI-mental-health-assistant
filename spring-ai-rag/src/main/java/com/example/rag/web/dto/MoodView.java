package com.example.rag.web.dto;

import com.example.rag.domain.MoodLog;

import java.util.Arrays;
import java.util.List;

public record MoodView(
        Long id,
        String sessionId,
        String user,
        String initials,
        String tone,
        String date,
        int score,
        int moodIndex,
        List<String> tags,
        String note,
        long createdAt
) {

    private static final int[] INDEX_BY_SCORE = {18, 39, 60, 79, 94};

    public static MoodView from(MoodLog log) {
        int safeScore = Math.max(1, Math.min(5, log.getScore()));
        List<String> tags = log.getTags() == null || log.getTags().isBlank()
                ? List.of()
                : Arrays.stream(log.getTags().split(","))
                        .map(String::trim)
                        .filter(text -> !text.isEmpty())
                        .toList();
        return new MoodView(
                log.getId(),
                log.getSessionId(),
                log.getUserName(),
                log.getInitials(),
                log.getTone(),
                log.getDate().toString(),
                safeScore,
                INDEX_BY_SCORE[safeScore - 1],
                tags,
                log.getNote() == null ? "" : log.getNote(),
                log.getCreatedAt() == null ? 0L : log.getCreatedAt().toEpochMilli()
        );
    }
}
