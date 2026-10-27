package com.example.rag.service;

import com.example.rag.domain.Consultation;
import com.example.rag.repository.ConsultationRepository;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.ConsultationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConsultationService {

    public static final String STATUS_FOLLOWED_UP = "已结束";

    private final ConsultationRepository consultationRepository;

    public ConsultationService(ConsultationRepository consultationRepository) {
        this.consultationRepository = consultationRepository;
    }

    @Transactional(readOnly = true)
    public List<ConsultationView> list() {
        return consultationRepository.findAllByOrderByStartedAtDesc().stream()
                .map(ConsultationView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConsultationView get(String id) {
        return ConsultationView.from(require(id));
    }

    @Transactional
    public ConsultationView followUp(String id) {
        Consultation consultation = require(id);
        consultation.setStatus(STATUS_FOLLOWED_UP);
        return ConsultationView.from(consultationRepository.save(consultation));
    }

    private Consultation require(String id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("咨询记录不存在：" + id));
    }
}
