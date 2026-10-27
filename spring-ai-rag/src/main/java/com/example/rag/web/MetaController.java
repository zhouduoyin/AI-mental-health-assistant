package com.example.rag.web;

import com.example.rag.repository.ChatMessageRepository;
import com.example.rag.repository.KnowledgeDocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "运行状态", description = "查看当前生效的模型 / 向量库 / 文档数量")
public class MetaController {

    private final VectorStore vectorStore;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeDocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;

    public MetaController(VectorStore vectorStore,
                          ObjectProvider<ChatModel> chatModelProvider,
                          EmbeddingModel embeddingModel,
                          KnowledgeDocumentRepository documentRepository,
                          ChatMessageRepository chatMessageRepository) {
        this.vectorStore = vectorStore;
        this.chatModelProvider = chatModelProvider;
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @GetMapping("/meta")
    @Operation(summary = "运行状态")
    public Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("vectorStore", vectorStore.getClass().getSimpleName());
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        meta.put("chatModel", chatModel == null ? "未配置" : chatModel.getClass().getSimpleName());
        meta.put("embeddingModel", embeddingModel.getClass().getSimpleName());
        meta.put("documents", documentRepository.count());
        meta.put("chunks", documentRepository.findAll().stream()
                .mapToInt(document -> document.getChunkCount())
                .sum());
        meta.put("conversations", chatMessageRepository.findConversationIds().size());
        return meta;
    }
}
