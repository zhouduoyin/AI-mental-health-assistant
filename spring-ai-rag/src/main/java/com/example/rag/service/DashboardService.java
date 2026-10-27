package com.example.rag.service;

import com.example.rag.domain.Consultation;
import com.example.rag.domain.MoodLog;
import com.example.rag.domain.UserAccount;
import com.example.rag.repository.ConsultationRepository;
import com.example.rag.repository.MoodLogRepository;
import com.example.rag.repository.UserAccountRepository;
import com.example.rag.web.dto.DashboardView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 后台仪表盘：所有指标都由数据库中的真实记录聚合得出，不再由前端随机浮动。
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FEED_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final MoodLogRepository moodLogRepository;
    private final ConsultationRepository consultationRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthTokenService tokenService;

    public DashboardService(MoodLogRepository moodLogRepository,
                            ConsultationRepository consultationRepository,
                            UserAccountRepository userAccountRepository,
                            AuthTokenService tokenService) {
        this.moodLogRepository = moodLogRepository;
        this.consultationRepository = consultationRepository;
        this.userAccountRepository = userAccountRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public DashboardView build() {
        LocalDate today = LocalDate.now();
        List<MoodLog> moods = moodLogRepository.findAllByOrderByDateDescCreatedAtDesc();
        List<Consultation> consultations = consultationRepository.findAllByOrderByStartedAtDesc();
        List<UserAccount> accounts = userAccountRepository.findAll();

        Map<LocalDate, List<MoodLog>> moodsByDay = moods.stream()
                .collect(Collectors.groupingBy(MoodLog::getDate));
        Map<LocalDate, List<Consultation>> consultsByDay = consultations.stream()
                .filter(item -> item.getStartedAt() != null)
                .collect(Collectors.groupingBy(item -> item.getStartedAt().toLocalDate()));

        List<LocalDate> trendDays = daysBetween(today.minusDays(13), today);
        List<LocalDate> weekDays = daysBetween(today.minusDays(6), today);
        List<LocalDate> previousWeek = daysBetween(today.minusDays(13), today.minusDays(7));

        List<String> trendLabels = trendDays.stream().map(day -> day.format(DAY_LABEL)).toList();
        List<Double> moodTrendValues = trendDays.stream()
                .map(day -> round1(averageScore(moodsByDay.getOrDefault(day, List.of()))))
                .toList();
        List<Double> activeTrendValues = trendDays.stream()
                .map(day -> (double) activeUsersOn(day, moodsByDay, consultsByDay).size())
                .toList();

        List<DashboardView.Bar> consultStats = weekDays.stream()
                .map(day -> new DashboardView.Bar(
                        WEEKDAYS[day.getDayOfWeek().getValue() - 1],
                        consultsByDay.getOrDefault(day, List.of()).size()))
                .toList();

        int totalMoods = moods.size();
        List<DashboardView.Slice> levelSlices = List.of(
                slice("积极 4–5 分", moods.stream().filter(item -> item.getScore() >= 4).count(), totalMoods,
                        "var(--warm-brand)"),
                slice("平稳 3 分", moods.stream().filter(item -> item.getScore() == 3).count(), totalMoods,
                        "var(--warm-lilac)"),
                slice("低情绪 1–2 分", moods.stream().filter(item -> item.getScore() <= 2).count(), totalMoods,
                        "var(--warm-apricot)")
        );

        List<DashboardView.Bar> triggerTop = topTags(moods);

        double moodTotal = moods.size();
        double recentMoods = moods.stream().filter(item -> !item.getDate().isBefore(today.minusDays(6))).count();
        double previousMoods = moods.stream()
                .filter(item -> !item.getDate().isBefore(today.minusDays(13)))
                .filter(item -> item.getDate().isBefore(today.minusDays(6)))
                .count();
        double consultTotal = consultations.size();
        double recentConsults = consultations.stream()
                .filter(item -> item.getStartedAt() != null)
                .filter(item -> !item.getStartedAt().toLocalDate().isBefore(today.minusDays(6)))
                .count();
        double previousConsults = consultations.stream()
                .filter(item -> item.getStartedAt() != null)
                .filter(item -> !item.getStartedAt().toLocalDate().isBefore(today.minusDays(13)))
                .filter(item -> item.getStartedAt().toLocalDate().isBefore(today.minusDays(6)))
                .count();

        Set<String> allUsers = new LinkedHashSet<>();
        moods.stream().map(MoodLog::getUserName).filter(name -> name != null && !name.isBlank()).forEach(allUsers::add);
        consultations.stream().map(Consultation::getUserName)
                .filter(name -> name != null && !name.isBlank()).forEach(allUsers::add);
        accounts.stream().map(UserAccount::getName).forEach(allUsers::add);

        Set<String> recentUsers = activeUsersBetween(weekDays, moodsByDay, consultsByDay);
        Set<String> previousUsers = activeUsersBetween(previousWeek, moodsByDay, consultsByDay);

        double recentAvg = averageScore(moods.stream()
                .filter(item -> !item.getDate().isBefore(today.minusDays(6))).toList());
        double previousAvg = averageScore(moods.stream()
                .filter(item -> !item.getDate().isBefore(today.minusDays(13)))
                .filter(item -> item.getDate().isBefore(today.minusDays(6)))
                .toList());
        double allAvg = averageScore(moods);

        List<DashboardView.Stat> stats = List.of(
                new DashboardView.Stat(
                        "users", "总用户数", allUsers.size(), growth(recentUsers.size(), previousUsers.size()),
                        "人", "users",
                        weekDays.stream().map(day -> (double) activeUsersOn(day, moodsByDay, consultsByDay).size()).toList()
                ),
                new DashboardView.Stat(
                        "moods", "情绪日志", moodTotal, growth(recentMoods, previousMoods),
                        "条", "mood",
                        weekDays.stream().map(day -> (double) moodsByDay.getOrDefault(day, List.of()).size()).toList()
                ),
                new DashboardView.Stat(
                        "consults", "咨询会话", consultTotal, growth(recentConsults, previousConsults),
                        "次", "chat",
                        weekDays.stream().map(day -> (double) consultsByDay.getOrDefault(day, List.of()).size()).toList()
                ),
                new DashboardView.Stat(
                        "moodAvg", "平均情绪", round1(allAvg), growth(recentAvg, previousAvg),
                        "分", "sparkles",
                        weekDays.stream()
                                .map(day -> round1(averageScore(moodsByDay.getOrDefault(day, List.of()))))
                                .toList()
                )
        );

        DashboardView.Live live = new DashboardView.Live(
                tokenService.onlineCount(),
                null,
                LocalDateTime.now().format(CLOCK),
                buildFeed(moods, consultations)
        );

        return new DashboardView(
                stats,
                new DashboardView.Trend(trendLabels, moodTrendValues),
                consultStats,
                new DashboardView.Trend(trendLabels, activeTrendValues),
                levelSlices,
                triggerTop,
                totalMoods,
                live
        );
    }

    private List<DashboardView.Feed> buildFeed(List<MoodLog> moods, List<Consultation> consultations) {
        record Event(LocalDateTime at, DashboardView.Feed feed) {
        }
        List<Event> events = new ArrayList<>();
        moods.stream().limit(10).forEach(log -> {
            LocalDateTime at = log.getDate().atTime(LocalTime.of(20, 0));
            String tag = firstTag(log.getTags());
            String action = "记录了 " + log.getScore() + " 分情绪" + (tag.isEmpty() ? "" : " · " + tag);
            events.add(new Event(at, new DashboardView.Feed(
                    "匿名 · " + log.getUserName(),
                    action,
                    feedTone(log.getTone()),
                    log.getDate().format(DAY_LABEL)
            )));
        });
        consultations.stream().limit(10).filter(item -> item.getStartedAt() != null).forEach(item ->
                events.add(new Event(item.getStartedAt(), new DashboardView.Feed(
                        "来访 · " + item.getUserName(),
                        "发起了一次「" + item.getTopic() + "」咨询",
                        feedTone(item.getTone()),
                        item.getStartedAt().format(FEED_TIME)
                )))
        );
        return events.stream()
                .sorted(Comparator.comparing(Event::at).reversed())
                .limit(5)
                .map(Event::feed)
                .toList();
    }

    private List<DashboardView.Bar> topTags(List<MoodLog> moods) {
        Map<String, Long> counts = new HashMap<>();
        for (MoodLog log : moods) {
            if (log.getTags() == null || log.getTags().isBlank()) {
                continue;
            }
            for (String tag : log.getTags().split(",")) {
                String value = tag.trim();
                if (!value.isEmpty()) {
                    counts.merge(value, 1L, Long::sum);
                }
            }
        }
        Map<String, Long> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        return sorted.entrySet().stream()
                .map(entry -> new DashboardView.Bar(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Set<String> activeUsersBetween(List<LocalDate> days, Map<LocalDate, List<MoodLog>> moodsByDay,
                                           Map<LocalDate, List<Consultation>> consultsByDay) {
        Set<String> names = new HashSet<>();
        days.forEach(day -> names.addAll(activeUsersOn(day, moodsByDay, consultsByDay)));
        return names;
    }

    private Set<String> activeUsersOn(LocalDate day, Map<LocalDate, List<MoodLog>> moodsByDay,
                                      Map<LocalDate, List<Consultation>> consultsByDay) {
        Set<String> names = new HashSet<>();
        moodsByDay.getOrDefault(day, List.of()).forEach(log -> names.add(log.getUserName()));
        consultsByDay.getOrDefault(day, List.of()).forEach(item -> names.add(item.getUserName()));
        names.remove(null);
        return names;
    }

    private DashboardView.Slice slice(String label, long count, int total, String color) {
        int percent = total == 0 ? 0 : (int) Math.round(count * 100.0 / total);
        return new DashboardView.Slice(label, percent, color);
    }

    private double averageScore(List<MoodLog> moods) {
        return moods.isEmpty() ? 0 : moods.stream().mapToInt(MoodLog::getScore).average().orElse(0);
    }

    private double growth(double recent, double previous) {
        if (previous == 0) {
            return recent == 0 ? 0 : 100;
        }
        return round1((recent - previous) / previous * 100);
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private List<LocalDate> daysBetween(LocalDate start, LocalDate end) {
        return IntStream.rangeClosed(0, (int) (end.toEpochDay() - start.toEpochDay()))
                .mapToObj(start::plusDays)
                .toList();
    }

    private String firstTag(String tags) {
        if (tags == null || tags.isBlank()) {
            return "";
        }
        return tags.split(",")[0].trim();
    }

    private String feedTone(String tone) {
        return switch (tone == null ? "" : tone) {
            case "lilac" -> "lilac";
            case "sand" -> "apricot";
            default -> "sage";
        };
    }
}
