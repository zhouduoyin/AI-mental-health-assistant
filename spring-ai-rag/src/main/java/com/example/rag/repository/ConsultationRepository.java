package com.example.rag.repository;

import com.example.rag.domain.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationRepository extends JpaRepository<Consultation, String> {

    List<Consultation> findAllByOrderByStartedAtDesc();

    long countByUserName(String userName);
}
