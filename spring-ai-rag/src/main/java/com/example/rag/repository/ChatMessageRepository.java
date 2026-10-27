package com.example.rag.repository;

import com.example.rag.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(String conversationId);

    List<ChatMessage> findTop10ByConversationIdOrderByIdDesc(String conversationId);

    Optional<ChatMessage> findFirstByConversationIdOrderByIdDesc(String conversationId);

    Optional<ChatMessage> findFirstByConversationIdAndRoleOrderByIdAsc(String conversationId, String role);

    long countByConversationId(String conversationId);

    long deleteByConversationId(String conversationId);

    /**
     * 按最后一条消息倒序列出所有会话 id，用于对话记录列表。
     */
    @Query("select m.conversationId from ChatMessage m group by m.conversationId order by max(m.id) desc")
    List<String> findConversationIds();
}
