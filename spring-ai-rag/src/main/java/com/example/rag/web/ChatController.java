package com.example.rag.web;

import com.example.rag.service.RagChatService;
import com.example.rag.web.dto.ChatAnswer;
import com.example.rag.web.dto.ChatAsk;
import com.example.rag.web.dto.ChatMessageView;
import com.example.rag.web.dto.ConversationSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "RAG 问答", description = "基于知识库的检索增强问答（支持流式输出）")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping
    @Operation(summary = "知识库问答", description = "conversationId 为空时自动新建会话；一次性返回完整回答")
    public ChatAnswer ask(@Valid @RequestBody ChatAsk request) {
        return ragChatService.ask(request);
    }

    /**
     * 流式问答（SSE）。事件：
     * <ul>
     *   <li>{@code meta}：conversationId + 检索到的参考资料</li>
     *   <li>{@code delta}：一段增量文本</li>
     *   <li>{@code done}：conversationId + 已保存的助手消息 id</li>
     *   <li>{@code error}：detail</li>
     * </ul>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式问答（SSE）")
    public SseEmitter stream(@Valid @RequestBody ChatAsk request) {
        return stream(ragChatService.start(request.conversationId(), request.question(), true));
    }

    @PostMapping(value = "/{conversationId}/regenerate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "重新生成最后一条回复（SSE）")
    public SseEmitter regenerate(@PathVariable String conversationId) {
        return stream(ragChatService.regenerate(conversationId));
    }

    @GetMapping("/conversations")
    @Operation(summary = "对话记录列表")
    public List<ConversationSummary> conversations() {
        return ragChatService.conversations();
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "查询会话历史")
    public List<ChatMessageView> history(@PathVariable String conversationId) {
        return ragChatService.history(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除会话历史")
    public void delete(@PathVariable String conversationId) {
        ragChatService.deleteConversation(conversationId);
    }

    private SseEmitter stream(RagChatService.StreamStart start) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "conversationId", start.conversationId(),
                    "references", start.references()), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        StringBuilder answer = new StringBuilder();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        Disposable subscription = start.spec().stream().content().subscribe(
                delta -> {
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    answer.append(delta);
                    try {
                        emitter.send(SseEmitter.event().name("delta")
                                .data(Map.of("text", delta), MediaType.APPLICATION_JSON));
                    } catch (IOException ex) {
                        Disposable current = subscriptionRef.get();
                        if (current != null) {
                            current.dispose();
                        }
                        emitter.complete();
                    }
                },
                error -> {
                    log.warn("流式问答失败 conversationId={}", start.conversationId(), error);
                    sendError(emitter, RagChatService.rootMessage(error));
                    emitter.complete();
                },
                () -> {
                    try {
                        String text = answer.toString().strip();
                        if (text.isEmpty()) {
                            throw new IllegalStateException("模型返回了空回答");
                        }
                        Long messageId = ragChatService.saveAssistant(start.conversationId(), text);
                        emitter.send(SseEmitter.event().name("done").data(Map.of(
                                "conversationId", start.conversationId(),
                                "messageId", messageId), MediaType.APPLICATION_JSON));
                        emitter.complete();
                    } catch (Exception ex) {
                        log.warn("保存流式回答失败 conversationId={}", start.conversationId(), ex);
                        sendError(emitter, RagChatService.rootMessage(ex));
                        emitter.complete();
                    }
                });
        subscriptionRef.set(subscription);
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(error -> subscription.dispose());
        return emitter;
    }

    private void sendError(SseEmitter emitter, String detail) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("detail", detail == null ? "未知错误" : detail), MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            // 客户端已断开，忽略
        }
    }
}
