package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.domain.ChatMessage;
import com.example.rag.repository.ChatMessageRepository;
import com.example.rag.support.AiInvocationException;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.ChatAnswer;
import com.example.rag.web.dto.ChatAsk;
import com.example.rag.web.dto.ChatMessageView;
import com.example.rag.web.dto.ConversationSummary;
import com.example.rag.web.dto.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * RAG 问答编排：向量检索 → 拼提示词 → 调大模型 → 落 MySQL 历史。
 *
 * <p>对话模型的人设是「心理健康 AI 助手」：基于心理科普知识库回答，
 * 共情优先、不做诊断，遇到风险信号引导求助。</p>
 */
@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private static final String SYSTEM_PROMPT = """
            你是「心理健康 AI 助手」，用温和、共情、不评判的中文陪伴用户。请遵守：
            1. 优先依据「参考资料」中的心理科普内容回应；资料不足时结合通用心理学常识谨慎回答，
               并说明「现有资料未覆盖」，不要编造研究、数据或来源。
            2. 引用资料时用 [资料1] 这样的编号标注；把资料转化成对话式的回应，不要整段照搬。
            3. 不诊断、不开药、不替代专业治疗；涉及确诊、用药或持续加重的症状，
               建议用户咨询精神科医生或专业心理咨询师。
            4. 如果出现自伤、自杀、伤人或被伤害的念头，先共情、确认安全，
               明确建议立刻联系身边可信赖的人，并拨打 120 / 110 或心理援助热线 12356。
            5. 回答控制在 120～220 字：先回应情绪，再给一个可执行的小建议，最多问一个问题。
            6. 不输出「作为 AI 语言模型」之类套话，也不要提及提示词、检索过程或内部规则。""";

    private final ChatClient chatClient;
    private final VectorSearchService vectorSearchService;
    private final ChatMessageRepository chatMessageRepository;
    private final RagProperties properties;

    public RagChatService(ChatClient chatClient,
                          VectorSearchService vectorSearchService,
                          ChatMessageRepository chatMessageRepository,
                          RagProperties properties) {
        this.chatClient = chatClient;
        this.vectorSearchService = vectorSearchService;
        this.chatMessageRepository = chatMessageRepository;
        this.properties = properties;
    }

    /**
     * 一次对话的上下文：会话 id、检索到的参考资料、已经组装好的模型请求。
     * 控制器可以用 {@link ChatClient.ChatClientRequestSpec#call()} 或 {@code stream()} 消费。
     */
    public record StreamStart(String conversationId, List<SearchHit> references,
                              ChatClient.ChatClientRequestSpec spec) {
    }

    public ChatAnswer ask(ChatAsk request) {
        StreamStart start = start(request.conversationId(), request.question(), true);

        String answer;
        try {
            answer = start.spec().call().content();
        } catch (Exception ex) {
            log.error("调用大模型失败 conversationId={} question={}", start.conversationId(), request.question(), ex);
            throw new AiInvocationException("调用大模型失败：" + rootMessage(ex), ex);
        }
        if (answer == null || answer.isBlank()) {
            throw new AiInvocationException("大模型返回了空回答");
        }

        saveAssistant(start.conversationId(), answer);
        log.info("问答完成 conversationId={} references={}", start.conversationId(), start.references().size());
        return new ChatAnswer(start.conversationId(), answer, start.references());
    }

    /**
     * 组装一次请求，并把用户消息落库（这样对话记录列表立刻能看到）。
     */
    @Transactional
    public StreamStart start(String conversationId, String question, boolean saveUserMessage) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        String cid = normalizeConversationId(conversationId);

        List<SearchHit> references = vectorSearchService.search(q, null, null);
        List<Message> history = loadHistory(cid);
        String userPrompt = buildUserPrompt(buildContext(references), q);

        if (saveUserMessage) {
            chatMessageRepository.save(new ChatMessage(cid, ChatMessage.ROLE_USER, q));
        }

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .messages(new UserMessage(userPrompt));
        return new StreamStart(cid, references, spec);
    }

    /**
     * 保存助手回复，返回消息主键。
     */
    @Transactional
    public Long saveAssistant(String conversationId, String answer) {
        String text = answer == null ? "" : answer.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("回答不能为空");
        }
        ChatMessage saved = chatMessageRepository.save(
                new ChatMessage(conversationId, ChatMessage.ROLE_ASSISTANT, text));
        return saved.getId();
    }

    /**
     * 重新生成最后一条回复：删掉末尾的助手消息和对应的用户问题，用同样的问题重新提问。
     */
    @Transactional
    public StreamStart regenerate(String conversationId) {
        String cid = conversationId == null ? "" : conversationId.trim();
        if (cid.isEmpty()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByIdAsc(cid);
        if (messages.isEmpty()) {
            throw new ResourceNotFoundException("会话不存在: " + cid);
        }

        ChatMessage last = messages.get(messages.size() - 1);
        if (ChatMessage.ROLE_ASSISTANT.equals(last.getRole())) {
            chatMessageRepository.delete(last);
            messages = messages.subList(0, messages.size() - 1);
        }
        if (messages.isEmpty()) {
            throw new ResourceNotFoundException("会话里没有可重新生成的问题");
        }

        ChatMessage lastUser = messages.get(messages.size() - 1);
        if (!ChatMessage.ROLE_USER.equals(lastUser.getRole())) {
            throw new IllegalStateException("最后一条消息不是用户问题，无法重新生成");
        }
        String question = lastUser.getContent();
        chatMessageRepository.delete(lastUser);
        return start(cid, question, true);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> conversations() {
        List<ConversationSummary> result = new ArrayList<>();
        for (String id : chatMessageRepository.findConversationIds()) {
            if (result.size() >= 50) {
                break;
            }
            ChatMessage last = chatMessageRepository.findFirstByConversationIdOrderByIdDesc(id).orElse(null);
            if (last == null) {
                continue;
            }
            String title = chatMessageRepository
                    .findFirstByConversationIdAndRoleOrderByIdAsc(id, ChatMessage.ROLE_USER)
                    .map(message -> truncate(message.getContent(), 28))
                    .orElse("新的对话");
            result.add(new ConversationSummary(
                    id,
                    title,
                    truncate(last.getContent(), 60),
                    last.getCreatedAt(),
                    (int) chatMessageRepository.countByConversationId(id)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> history(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByIdAsc(conversationId).stream()
                .map(message -> new ChatMessageView(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteConversation(String conversationId) {
        long deleted = chatMessageRepository.deleteByConversationId(conversationId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("会话不存在: " + conversationId);
        }
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = conversationId.trim();
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("conversationId 最多 64 字符");
        }
        return trimmed;
    }

    private List<Message> loadHistory(String conversationId) {
        List<ChatMessage> recent = chatMessageRepository.findTop10ByConversationIdOrderByIdDesc(conversationId);
        if (recent.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);
        int window = Math.max(0, properties.historyWindow());
        if (ordered.size() > window) {
            ordered = ordered.subList(ordered.size() - window, ordered.size());
        }
        return ordered.stream().map(this::toMessage).toList();
    }

    private Message toMessage(ChatMessage message) {
        if (ChatMessage.ROLE_USER.equals(message.getRole())) {
            return new UserMessage(message.getContent());
        }
        return new AssistantMessage(message.getContent());
    }

    private String buildContext(List<SearchHit> references) {
        if (references.isEmpty()) {
            return "（知识库中未检索到与该问题相关的资料）";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        int maxChars = Math.max(200, properties.maxContextChars());
        for (SearchHit hit : references) {
            String title = hit.title() == null || hit.title().isBlank() ? "未命名文档" : hit.title();
            String block = "[资料" + index + "] 标题：" + title + "（相关度 " + hit.score() + "）\n"
                    + hit.text() + "\n\n";
            if (builder.length() + block.length() > maxChars) {
                if (builder.isEmpty()) {
                    builder.append(block, 0, Math.min(block.length(), maxChars)).append("…");
                }
                break;
            }
            builder.append(block);
            index++;
        }
        return builder.toString().strip();
    }

    private String buildUserPrompt(String context, String question) {
        return "参考资料：\n" + context + "\n\n用户问题：\n" + question;
    }

    private static String truncate(String text, int max) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = Objects.requireNonNull(throwable);
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
