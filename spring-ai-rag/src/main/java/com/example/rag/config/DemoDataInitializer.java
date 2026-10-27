package com.example.rag.config;

import com.example.rag.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * demo / local 启动时的知识库准备：
 * <ul>
 *   <li>知识库为空：写入心理科普示例文档（同时完成向量化）</li>
 *   <li>知识库已有文档但向量库是内存实现：重建向量索引（local 重启后不丢检索能力）</li>
 * </ul>
 */
@Component
@Profile({"demo", "local", "milvus"})
@ConditionalOnProperty(name = "rag.demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final String SOURCE = "心理健康知识库";

    private static final String SLEEP = """
            睡眠与情绪互相影响：睡不好会放大焦虑和烦躁，情绪紧绷又会让入睡更难。
            如果只是偶尔失眠，可以先做三件小事：一是固定起床时间，哪怕前一晚睡得少，
            也尽量在同一时间起床，让生物钟稳定；二是睡前一小时调暗灯光、放下手机，
            把刷不完的信息留到白天；三是躺下 20 分钟仍睡不着就起身，到昏暗的地方做点
            无聊的事，困了再回床上，避免让床和「着急」绑定。
            白天补觉尽量不超过 30 分钟，下午之后少喝咖啡和浓茶。睡前可以写三行「待办清单」，
            把没做完的事放到纸上，告诉自己明天再处理。
            如果失眠每周出现三次以上、持续一个月，或已经明显影响工作和情绪，
            建议到睡眠门诊或精神心理科评估，不要长期自行服用安眠药。""";

    private static final String ANXIETY = """
            焦虑是身体在说「有重要的事，需要准备」。心跳加快、手心出汗、脑子停不下来，
            都是交感神经被激活的表现，本身并不危险。
            当下可以先做一轮缓慢呼吸：吸气 4 秒、停 2 秒、呼气 6 秒，重复 5 轮，
            让呼气比吸气更长，帮助身体从警觉状态降下来。然后把担心的事写下来，
            分成「现在能做的」和「暂时做不了的」两列，只挑一件两分钟内能启动的小事去做。
            焦虑常常来自「必须做好」的念头。可以试着把它改成「我先完成一个粗糙版本」，
            降低启动门槛。规律吃饭、晒太阳、每天走 20 分钟，对稳定情绪也有帮助。
            如果焦虑持续超过两周、出现回避行为或频繁惊恐发作，建议寻求专业评估。""";

    private static final String LOW_MOOD = """
            情绪低落时，最先消失的往往是做事的力气。这不是懒，也不是意志力差，
            而是身体和情绪在提醒你需要休息与支持。
            可以先把目标缩小到「最低限度」：起床、喝水、吃一点东西、洗把脸。
            完成其中任何一件，都算今天的一次行动。不要用「我应该更好」来责备自己，
            低落时的自我批评会让情绪更低。
            试着每天记录一件微小的事：窗外的光、一顿热饭、和谁说了一句话。
            如果愿意，可以把感受告诉一个可信赖的人，哪怕只说「我最近不太好」。
            低落持续两周以上，或伴随明显兴趣减退、食欲和睡眠改变、注意力下降，
            建议到精神心理科做一次评估。若出现不想活、想伤害自己的念头，请立即求助。""";

    private static final String BOUNDARY = """
            关系里的很多疲惫，来自边界不清：不好意思拒绝、习惯性照顾别人、
            把别人的情绪当成自己的责任。
            边界不是把别人推开，而是说清楚「我能做什么、我不能做什么」。
            可以先用一句缓冲：「我需要想一下，晚点回复你」，给自己留出选择空间；
            再用「我」开头表达感受与需要，例如「我今天很累，想先休息，明天再聊」。
            拒绝时不必编很长理由，简短、稳定、重复就好。
            如果对方因此生气，那是对方的情绪，不需要你立刻负责。
            练习边界可以从低风险场景开始：对推销说不用、对不合理的加班先问清楚范围。
            长期处在被控制、被贬低或害怕表达的关系里，建议寻求心理咨询或可信任的支持网络。""";

    private static final String HELP = """
            什么时候该找专业帮助？可以从持续时间、影响程度和风险三个维度看。
            持续时间：情绪问题几乎每天出现、持续两周以上；影响程度：已经明显影响睡眠、食欲、
            工作学习或人际关系；风险：出现自伤、自杀念头，或依赖酒精、药物来缓解。
            满足其中任何一条，都值得预约精神心理科或专业心理咨询。
            心理咨询适合处理情绪困扰、关系议题、创伤经历和成长议题；
            精神科医生可以进行诊断与药物治疗，两者并不冲突。
            第一次就诊可以提前写下：症状从什么时候开始、最难受的时刻、睡眠和食欲变化、
            既往治疗经历。若正在服药，不要自行停药或加减量。
            寻求帮助不是软弱，而是对自己负责的一种能力。""";

    private static final String CRISIS = """
            如果你或身边的人出现自伤、自杀念头，或已经做出危险行为，请把安全放在第一位。
            第一步：不要独处，立刻联系一位可信赖的家人、朋友或同事，让对方陪着你。
            第二步：移开可能造成伤害的物品，离开高处、水边等危险环境。
            第三步：拨打 120 或 110 寻求紧急帮助；也可以拨打全国心理援助热线 12356，
            或当地 24 小时心理危机干预热线。中国大陆可拨打 12356 获得心理支持。
            第四步：如果已经服药过量或受伤，不要等待，立即去医院急诊。
            陪伴者可以做什么：认真听、不评判、不急着讲道理，直接问「你现在有危险吗」，
            并陪同就医。谈论自杀不会「教坏」对方，反而能降低孤立感。
            请记住：危机中的念头会变化，先活过今晚，明天再一起想办法。""";

    private final DocumentService documentService;

    public DemoDataInitializer(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (documentService.isEmpty()) {
            documentService.ingest("睡眠与情绪：今晚可以先做的几件小事", SOURCE, SLEEP);
            documentService.ingest("焦虑来临时，身体在发生什么", SOURCE, ANXIETY);
            documentService.ingest("情绪低落时，如何照顾自己", SOURCE, LOW_MOOD);
            documentService.ingest("关系里的边界：学会说「不」", SOURCE, BOUNDARY);
            documentService.ingest("什么时候需要寻求专业帮助", SOURCE, HELP);
            documentService.ingest("危机时刻：如何求助与陪伴", SOURCE, CRISIS);
            log.info("心理健康知识库已写入 6 篇示例文档（关闭方式：rag.demo.seed=false）");
            return;
        }
        documentService.rebuildIndex();
        log.info("知识库已有文档，已重建向量索引（local/demo/milvus 模式）");
    }
}
