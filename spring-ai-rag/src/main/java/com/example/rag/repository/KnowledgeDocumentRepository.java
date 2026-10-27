package com.example.rag.repository;

import com.example.rag.domain.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByIdDesc();
}
