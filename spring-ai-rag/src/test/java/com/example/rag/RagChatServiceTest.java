package com.example.rag;

import com.example.rag.service.DocumentService;
import com.example.rag.service.RagChatService;
import com.example.rag.web.dto.ChatAnswer;
import com.example.rag.web.dto.ChatAsk;
import com.example.rag.web.dto.IngestRequest;
import com.example.rag.web.dto.SearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用假的 ChatModel 验证 RAG 编排：检索上下文确实被拼进了提示词，历史消息能落库。
 */
@SpringBootTest(properties = "rag.demo.seed=false")
@ActiveProfiles("demo")
class RagChatServiceTest {

    @Autowired
    private RagChatService ragChatService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ChatModel chatModel;

    @Test
    @DisplayName("问答会把检索到的资料拼进提示词，并保存会话历史")
    void askBuildsPromptWithContextAndSavesHistory() {
        documentService.ingest(new IngestRequest(
                "RAG 说明",
                "test",
                "RAG 会先用向量数据库检索相关片段，再把片段作为参考资料交给大模型回答。"));

        ChatAnswer answer = ragChatService.ask(new ChatAsk("conv-1", "RAG 是怎么工作的？"));

        assertThat(answer.conversationId()).isEqualTo("conv-1");
        assertThat(answer.answer()).isEqualTo(StubChatModel.ANSWER);
        assertThat(answer.references()).isNotEmpty();
        assertThat(answer.references().get(0).title()).isEqualTo("RAG 说明");

        StubChatModel stub = (StubChatModel) chatModel;
        List<Message> instructions = stub.lastPrompt.getInstructions();
        String promptText = instructions.stream().map(Message::getText).collect(Collectors.joining("\n"));
        assertThat(promptText)
                .contains("参考资料")
                .contains("RAG 会先用向量数据库检索相关片段")
                .contains("RAG 是怎么工作的？");
        assertThat(instructions).anyMatch(message -> message instanceof UserMessage);

        assertThat(ragChatService.history("conv-1"))
                .hasSize(2)
                .extracting(view -> view.role())
                .containsExactly("USER", "ASSISTANT");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubChatConfiguration {

        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new StubChatModel();
        }
    }

    static class StubChatModel implements ChatModel {

        static final String ANSWER = "这是测试用的知识库回答";

        private Prompt lastPrompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(ANSWER))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }
    }
}
