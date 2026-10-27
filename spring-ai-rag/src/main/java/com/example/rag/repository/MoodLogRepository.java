package com.example.rag.repository;

import com.example.rag.domain.MoodLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MoodLogRepository extends JpaRepository<MoodLog, Long> {

    List<MoodLog> findByUserIdOrderByDateDesc(Long userId);

    Optional<MoodLog> findByUserIdAndDate(Long userId, LocalDate date);

    List<MoodLog> findAllByOrderByDateDescCreatedAtDesc();

    List<MoodLog> findByDateBetween(LocalDate from, LocalDate to);

    long countByUserId(Long userId);
}
