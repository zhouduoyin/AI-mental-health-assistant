package com.example.rag.service;

import com.example.rag.domain.MoodLog;
import com.example.rag.domain.UserAccount;
import com.example.rag.repository.MoodLogRepository;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.MoodRequest;
import com.example.rag.web.dto.MoodView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class MoodService {

    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MoodLogRepository moodLogRepository;

    public MoodService(MoodLogRepository moodLogRepository) {
        this.moodLogRepository = moodLogRepository;
    }

    @Transactional(readOnly = true)
    public List<MoodView> listMine(UserAccount user) {
        return moodLogRepository.findByUserIdOrderByDateDesc(user.getId()).stream()
                .map(MoodView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MoodView> listAll() {
        return moodLogRepository.findAllByOrderByDateDescCreatedAtDesc().stream()
                .map(MoodView::from)
                .toList();
    }

    /**
     * 同一天只保留一条记录：已存在则更新，避免首页与情绪日记各写一条。
     */
    @Transactional
    public MoodView save(UserAccount user, MoodRequest request) {
        LocalDate date = request.date() == null ? LocalDate.now() : request.date();
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("不能记录未来日期的情绪");
        }
        String tags = normalizeTags(request.tags());
        String note = request.note() == null ? "" : request.note().trim();
        MoodLog log = moodLogRepository.findByUserIdAndDate(user.getId(), date)
                .orElseGet(() -> new MoodLog(
                        user.getId(),
                        user.getName(),
                        initialsOf(user.getName()),
                        toneOf(user),
                        "M-" + date.format(SESSION_DATE),
                        date,
                        request.score(),
                        tags,
                        note
                ));
        log.setScore(request.score());
        log.setTags(tags);
        log.setNote(note);
        log.setUserName(user.getName());
        log.setInitials(initialsOf(user.getName()));
        log.setTone(toneOf(user));
        return MoodView.from(moodLogRepository.save(log));
    }

    @Transactional
    public void deleteMine(UserAccount user, Long id) {
        MoodLog log = moodLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("情绪记录不存在：" + id));
        if (!Objects.equals(log.getUserId(), user.getId())) {
            throw new ResourceNotFoundException("情绪记录不存在：" + id);
        }
        moodLogRepository.delete(log);
    }

    @Transactional
    public void deleteAny(Long id) {
        MoodLog log = moodLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("情绪记录不存在：" + id));
        moodLogRepository.delete(log);
    }

    private String normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = tag.trim();
            if (!value.isEmpty()) {
                unique.add(value.length() > 16 ? value.substring(0, 16) : value);
            }
            if (unique.size() >= 5) {
                break;
            }
        }
        String joined = String.join(",", unique);
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    private String initialsOf(String name) {
        String value = name == null ? "" : name.trim();
        return value.isEmpty() ? "访客" : value.substring(0, 1);
    }

    private String toneOf(UserAccount user) {
        return UserAccount.ROLE_BACK.equals(user.getRole()) ? "sand" : "teal";
    }
}
