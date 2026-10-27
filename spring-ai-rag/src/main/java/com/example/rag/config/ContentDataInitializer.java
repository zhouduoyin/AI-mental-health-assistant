package com.example.rag.config;

import com.example.rag.domain.Article;
import com.example.rag.domain.Consultation;
import com.example.rag.domain.MoodLog;
import com.example.rag.domain.UserAccount;
import com.example.rag.repository.ArticleRepository;
import com.example.rag.repository.ConsultationRepository;
import com.example.rag.repository.MoodLogRepository;
import com.example.rag.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 演示账号、文章、咨询与情绪记录种子数据。
 *
 * <p>所有日期都相对启动当天生成，避免示例数据随时间过期。
 * 每类数据仅在表为空时写入，不会覆盖用户后来新增或删除的内容。</p>
 */
@Component
@Profile({"demo", "local", "milvus"})
@ConditionalOnProperty(name = "rag.demo.seed", havingValue = "true", matchIfMissing = true)
public class ContentDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentDataInitializer.class);
    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserAccountRepository userAccountRepository;
    private final ArticleRepository articleRepository;
    private final ConsultationRepository consultationRepository;
    private final MoodLogRepository moodLogRepository;

    public ContentDataInitializer(UserAccountRepository userAccountRepository,
                                  ArticleRepository articleRepository,
                                  ConsultationRepository consultationRepository,
                                  MoodLogRepository moodLogRepository) {
        this.userAccountRepository = userAccountRepository;
        this.articleRepository = articleRepository;
        this.consultationRepository = consultationRepository;
        this.moodLogRepository = moodLogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UserAccount front = seedAccounts();
        seedArticles();
        seedConsultations();
        seedMoods(front);
    }

    /**
     * 演示账号：front / front（来访者）、back / back（咨询师 · 管理员），
     * 另加几个不同状态的后台账号，方便体验用户管理。
     * 演示项目明文存储密码，正式环境请改为 BCrypt 等哈希方案。
     */
    private UserAccount seedAccounts() {
        UserAccount front = seedAccount("front", "front", "小满", "来访者",
                UserAccount.ROLE_FRONT, true, 1);
        seedAccount("back", "back", "林医生", "咨询师 · 管理员",
                UserAccount.ROLE_BACK, true, 0);
        seedAccount("chenyu", "demo123", "陈雨", "来访者",
                UserAccount.ROLE_FRONT, true, 3);
        seedAccount("sunlan", "demo123", "孙岚", "咨询师",
                UserAccount.ROLE_BACK, true, 2);
        seedAccount("zhouyu", "demo123", "周予", "来访者",
                UserAccount.ROLE_FRONT, false, -1);
        return front;
    }

    private UserAccount seedAccount(String account, String password, String name, String title,
                                    String role, boolean enabled, int lastLoginDaysAgo) {
        return userAccountRepository.findByAccountIgnoreCase(account).orElseGet(() -> {
            UserAccount user = new UserAccount(account, password, name, title, role);
            user.setEnabled(enabled);
            if (lastLoginDaysAgo >= 0) {
                user.setLastLoginAt(Instant.now().minus(Duration.ofDays(lastLoginDaysAgo)));
            }
            return userAccountRepository.save(user);
        });
    }

    private void seedArticles() {
        if (articleRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<Article> articles = new ArrayList<>();
        articles.add(article(
                "如何与「还没发生的事」和平共处", "焦虑", "林医生",
                "焦虑常常不是关于现在，而是关于想象出来的未来。三个小练习，把注意力拉回此刻。",
                1240, Article.STATUS_PUBLISHED, "teal", today.minusDays(2),
                "焦虑的大脑擅长预演灾难：还没开口，就先想到被否定；还没出发，就先担心迟到。它并不是敌人，而是一套过度负责的预警系统。",
                "第一个练习是「命名」：当心跳加快时，在心里说一遍「我现在感到焦虑」。把情绪从背景噪音变成可以命名的对象，前额叶就重新上线了。",
                "第二个练习是「五感落地」：说出眼前看到的 3 样东西、听到的 2 种声音、身体接触到的 1 个表面。它不解决问题，但能把你从未来拽回现在。",
                "第三个练习是「可控圈」：拿一张纸，左边写「我能影响的」，右边写「我控制不了的」。把注意力放回左栏，哪怕只有一件小事。"
        ));
        articles.add(article(
                "睡前 10 分钟，把大脑调成飞行模式", "睡眠", "周医生",
                "入睡困难往往不是身体不累，而是大脑还在开会。一套温和的睡前降速流程。",
                986, Article.STATUS_PUBLISHED, "lilac", today.minusDays(3),
                "睡眠不是「关掉」，而是「降速」。睡前 10 分钟的目标，是让神经系统从交感模式切到副交感模式。",
                "先把屏幕放下，调暗灯光。可以写三行「明天再说」清单，把未完成的事交给纸面，大脑才肯放手。",
                "再做 4 轮缓慢呼吸：吸气 4 秒、屏息 4 秒、呼气 6 秒。呼气比吸气长，是在给身体发「安全」的信号。",
                "如果 20 分钟仍睡不着，起身到昏暗处做些无聊的事，困了再回床。不要在床上和清醒较劲。"
        ));
        articles.add(article(
                "当亲近的人说「你想太多了」", "关系", "林医生",
                "被否定感受时，比争论更有用的，是先照顾好自己，再决定怎么回应。",
                1580, Article.STATUS_PUBLISHED, "sand", today.minusDays(4),
                "「你想太多了」常常不是恶意，而是对方不知道怎么接住你的情绪。但这句话落在敏感的人身上，会变成二次伤害。",
                "先别急着证明自己没想多。可以试着说：「我不是要你解决，我只是想被听见。」把需求说清楚，比争论对错更有效。",
                "如果对方反复无法回应，也可以把支持系统分散到朋友、社群或专业咨询上，而不是把全部期待压在一个人身上。"
        ));
        articles.add(article(
                "情绪不是敌人：先给它一个名字", "自我成长", "苏医生",
                "准确命名情绪，能显著降低它的强度。这是一份比「我很难受」更细的情绪词表。",
                2104, Article.STATUS_PUBLISHED, "teal", today.minusDays(5),
                "心理学里有个说法：能说清楚的感受，更容易被调节。模糊的痛苦会持续报警，命名的痛苦才有边界。",
                "把「难受」拆开：是委屈、失落、羞耻，还是被忽视的愤怒？每种情绪对应不同的需要。",
                "试着写一句完整的话：「当____发生时，我感到____，因为我需要____。」这不是作文，而是给情绪一个可被照顾的位置。"
        ));
        articles.add(article(
                "焦虑发作时的 4-7-8 呼吸法", "焦虑", "周医生",
                "一分钟可上手的急救式呼吸，帮助心率回落。注意：不适时随时停止，以自然呼吸为准。",
                762, Article.STATUS_PUBLISHED, "sand", today.minusDays(7),
                "吸气 4 秒，屏息 7 秒，呼气 8 秒，重复 4 轮。呼气延长会激活副交感神经，让心率慢慢降下来。",
                "如果屏息让你更紧张，就改成吸气 4 秒、呼气 6 秒，不必勉强。呼吸练习的目标是温和，不是标准答案。",
                "若出现胸痛、晕厥或持续心悸，请先就医排查身体原因，再考虑情绪因素。"
        ));
        articles.add(article(
                "低能量时，先做这三件小事", "自我成长", "林医生",
                "状态差的时候，目标不是振作，而是维持。把门槛降到不可能失败。",
                1355, Article.STATUS_DRAFT, "lilac", null,
                "低能量时列长清单，只会制造新的挫败。把今天的目标缩小到「不可能失败」：喝一杯水、拉开窗帘、洗把脸。",
                "完成之后不要评价做得好不好，只记录「我做了」。微小行动的累积，比一次性的振作更可靠。",
                "如果低落持续两周以上，并影响进食、睡眠或工作，请考虑寻求专业帮助。"
        ));
        articles.add(article(
                "把「我应该」换成「我可以」", "自我成长", "苏医生",
                "语言会悄悄塑造选择。三个替换练习，把紧绷的自我要求松下来。",
                1188, Article.STATUS_PUBLISHED, "teal", today.minusDays(8),
                "「我应该早点睡」「我应该更努力」——这些句子听起来像提醒，实际更像审判。它们把选择变成了义务，也把失败提前写好了。",
                "第一个替换：把「我应该」改成「我可以」。我可以早点睡，也可以今晚再忙一会儿。前者留出了余地，身体才愿意配合。",
                "第二个替换：把「必须」改成「想要」。问自己：我真正想要的是什么？如果答案是「想被看见」，那也许不必靠熬夜完成工作来换。",
                "第三个替换：把「又没做到」改成「这次做到了几分」。进步不是全有或全无，给过程打分，比给结果判刑更有用。"
        ));
        articles.add(article(
                "一个人吃饭，也可以很安心", "关系", "周医生",
                "独处不是被落下。把一顿饭还给自己，练习不被打扰的陪伴。",
                894, Article.STATUS_PUBLISHED, "sand", today.minusDays(9),
                "一个人吃饭时，很多人会下意识地刷手机，让屏幕上的人声填满安静。但独处本身并不危险，危险的是我们对自己的评判。",
                "试着把第一口饭吃完再拿起手机：看清食物的颜色，闻一闻气味，咀嚼到可以咽下为止。这是一次很小的正念练习。",
                "如果孤独感涌上来，不要急着赶走它。可以在心里说一句：「我现在一个人，但我在陪自己。」被自己陪着，也是一种关系。",
                "偶尔约朋友，偶尔独自吃饭。两种状态都可以，不必把任何一种过成证明。"
        ));
        articleRepository.saveAll(articles);
        log.info("已写入 {} 篇演示文章", articles.size());
    }

    private Article article(String title, String category, String author, String summary, long views,
                            String status, String cover, LocalDate publishedAt, String... paragraphs) {
        int chars = 0;
        for (String paragraph : paragraphs) {
            chars += paragraph.length();
        }
        int minutes = Math.max(2, (int) Math.ceil(chars / 280.0));
        LocalDate updated = status.equals(Article.STATUS_DRAFT) ? LocalDate.now().minusDays(1) : publishedAt;
        return new Article(title, category, author, summary, String.join("\n\n", paragraphs),
                minutes, views, status, cover, publishedAt, updated);
    }

    private void seedConsultations() {
        if (consultationRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<Consultation> items = new ArrayList<>();
        items.add(consultation("C-1024", "林小满", "林", "teal", today.minusDays(1).atTime(9, 24),
                12, "职场沟通", "进行中", "低", "焦虑", 18,
                new SeedMessage("user", "最近开会总是不敢发言，怕说错。", "09:24"),
                new SeedMessage("ai", "听起来你在会议里承担了不小的压力。愿意说说，最近一次让你紧张的会议发生了什么吗？", "09:25"),
                new SeedMessage("user", "轮到我汇报时，心跳特别快，声音都在抖。", "09:27"),
                new SeedMessage("ai", "下次开会前，我们先练一遍开场那句。要不要现在试一次？", "09:28")));
        items.add(consultation("C-1023", "陈一诺", "陈", "lilac", today.minusDays(2).atTime(21, 10),
                28, "睡眠困扰", "已结束", "中", "低落", 26,
                new SeedMessage("user", "连续一周凌晨三点醒，白天完全没精神。", "21:10"),
                new SeedMessage("ai", "早醒确实很消耗人。我们先看看：醒来时你通常会想什么？", "21:12"),
                new SeedMessage("user", "想工作上的事，越想越清醒。", "21:14"),
                new SeedMessage("ai", "今晚先试试睡前 10 分钟降速流程，明天的记录里告诉我效果。", "21:16")));
        items.add(consultation("C-1022", "周迟", "周", "sand", today.minusDays(2).atTime(15, 2),
                8, "亲密关系", "待跟进", "需关注", "低落", 14,
                new SeedMessage("user", "和伴侣吵架后，我一直很自责。", "15:02"),
                new SeedMessage("ai", "自责往往说明你很在意这段关系。愿意说说当时发生了什么吗？", "15:03"),
                new SeedMessage("user", "我说话很冲，事后又后悔。", "15:05"),
                new SeedMessage("ai", "自责说明你很在意。下次想冲口而出时，先停三秒再回应。", "15:07")));
        items.add(consultation("C-1021", "苏念", "苏", "teal", today.minusDays(3).atTime(19, 40),
                22, "自我价值", "已结束", "低", "低落", 32,
                new SeedMessage("user", "总觉得自己不够好，做什么都没意义。", "19:40"),
                new SeedMessage("ai", "这种「不够好」的声音，通常很早就出现了。它第一次出现是什么时候？", "19:42"),
                new SeedMessage("user", "可能是上学的时候吧。", "19:44"),
                new SeedMessage("ai", "那个声音不是事实。下次一起练习把它和真实的自己分开。", "19:46")));
        items.add(consultation("C-1020", "何雨", "何", "lilac", today.minusDays(3).atTime(11, 5),
                16, "焦虑缓解", "进行中", "中", "焦虑", 11,
                new SeedMessage("user", "下午会突然心悸，检查过身体没问题。", "11:05"),
                new SeedMessage("ai", "身体检查排除了器质性问题，这很有帮助。心悸发生时，你通常在做什么？", "11:07"),
                new SeedMessage("user", "一般是会议前，或者想到还没做完的事。", "11:09"),
                new SeedMessage("ai", "会议前先做 4 轮慢呼吸，把注意力放在脚底和呼吸上。", "11:11")));
        items.add(consultation("C-1019", "唐棠", "唐", "sand", today.minusDays(4).atTime(8, 30),
                19, "家庭沟通", "已结束", "低", "焦虑", 9,
                new SeedMessage("user", "和父母一说话就吵架。", "08:30"),
                new SeedMessage("ai", "和父母沟通常常夹着期待和旧账。最近一次争执是围绕什么？", "08:32"),
                new SeedMessage("user", "他们总催我结婚。", "08:34"),
                new SeedMessage("ai", "和父母沟通前，先约定一个「暂停」信号，避免越吵越远。", "08:36")));
        items.add(consultation("C-1018", "江野", "江", "lilac", today.minusDays(5).atTime(22, 15),
                6, "情绪低落", "待跟进", "需关注", "低落", 21,
                new SeedMessage("user", "最近什么都不想做，也不想说话。", "22:15"),
                new SeedMessage("ai", "谢谢你说出来。这种状态持续多久了？有没有影响到吃饭和睡觉？", "22:16"),
                new SeedMessage("user", "大概两周了，吃得很少。", "22:18"),
                new SeedMessage("ai", "谢谢你信任我。接下来两天先保证吃饭和睡眠，我们再一起看下一步。", "22:20")));
        items.add(consultation("C-1017", "顾清", "顾", "teal", today.minusDays(6).atTime(16, 20),
                31, "学业压力", "已结束", "低", "焦虑", 24,
                new SeedMessage("user", "复习效率很低，越焦虑越看不进去。", "16:20"),
                new SeedMessage("ai", "焦虑会占用工作记忆，看不进去并不代表你不努力。我们试试把任务拆小？", "16:22"),
                new SeedMessage("user", "好，可我不知道从哪开始。", "16:24"),
                new SeedMessage("ai", "把复习拆成 20 分钟一段，每段只做一件事，焦虑会低一些。", "16:26")));
        consultationRepository.saveAll(items);
        log.info("已写入 {} 条演示咨询记录", items.size());
    }

    private Consultation consultation(String id, String user, String initials, String tone,
                                      LocalDateTime startedAt, int duration, String topic, String status,
                                      String risk, String mood, int messageCount, SeedMessage... messages) {
        Consultation consultation = new Consultation(id, user, initials, tone, startedAt, duration,
                topic, status, risk, mood, messageCount);
        for (SeedMessage message : messages) {
            consultation.addMessage(message.role(), message.text(), message.time());
        }
        return consultation;
    }

    /**
     * 近 14 天情绪记录：前台账号每天一条（用于情绪日记与首页），
     * 另有 8 位匿名来访者的记录，供后台统计与筛选。
     */
    private void seedMoods(UserAccount front) {
        if (moodLogRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        int[] frontScores = {3, 4, 2, 3, 4, 3, 5, 4, 3, 2, 3, 4, 4, 5};
        String[][] frontTags = {
                {"工作压力", "睡眠"}, {"运动"}, {"亲密关系"}, {"放松"},
                {"工作压力"}, {"睡眠"}, {"运动", "放松"}, {"自我成长"},
                {"焦虑"}, {"睡眠"}, {"家庭"}, {"运动"},
                {"工作压力", "焦虑"}, {"放松", "自我成长"}
        };
        String[] frontNotes = {
                "今天完成了汇报，紧张但顺利。",
                "晨跑后心情不错。",
                "和伴侣有点争执，晚上没怎么睡好。",
                "给自己留了一个不被打扰的下午。",
                "事情有点多，脑子停不下来。",
                "睡前做了呼吸练习，稍微放松一些。",
                "散步半小时，感觉身体轻了。",
                "想清楚了一件拖很久的事。",
                "下午有点心慌，做了几次深呼吸。",
                "凌晨醒了一次，白天有些疲惫。",
                "和家里通了电话，心情复杂但踏实。",
                "运动完出了汗，情绪好多了。",
                "会议前有些紧张，但说完了想说的话。",
                "今天状态不错，给自己记一分。"
        };
        String[] otherNames = {"林小满", "陈一诺", "周迟", "苏念", "何雨", "唐棠", "江野", "顾清"};
        String[] otherTones = {"teal", "lilac", "sand", "teal", "lilac", "sand", "lilac", "teal"};
        String[] otherTags = {"工作压力", "睡眠", "亲密关系", "自我成长", "焦虑", "家庭", "睡眠", "学业压力"};
        String[] otherNotes = {
                "开会前有点紧张，但完成了汇报。",
                "连续几晚没睡好，白天没精神。",
                "和伴侣吵架后很自责。",
                "完成了拖很久的事，很有成就感。",
                "下午心悸，做了几次呼吸练习。",
                "和孩子一起做饭，很放松。",
                "一整天不想说话。",
                "复习效率一般，但坚持了两小时。"
        };
        int[] otherScores = {3, 2, 2, 5, 3, 4, 1, 3};

        List<MoodLog> logs = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate date = today.minusDays(13 - i);
            logs.add(new MoodLog(
                    front.getId(), front.getName(), "小", "teal",
                    "M-" + date.format(SESSION_DATE), date,
                    frontScores[i], String.join(",", frontTags[i]), frontNotes[i]
            ));
            for (int j = 0; j < 4; j++) {
                int index = (i * 3 + j) % otherNames.length;
                logs.add(new MoodLog(
                        null, otherNames[index], otherNames[index].substring(0, 1), otherTones[index],
                        "M-" + date.format(SESSION_DATE), date,
                        otherScores[index], otherTags[index], otherNotes[index]
                ));
            }
        }
        moodLogRepository.saveAll(logs);
        log.info("已写入 {} 条演示情绪记录（近 14 天）", logs.size());
    }

    private record SeedMessage(String role, String text, String time) {
    }
}
