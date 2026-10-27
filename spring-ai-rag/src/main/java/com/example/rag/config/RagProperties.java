package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * RAG 相关可调参数。
 *
 * @param topK                 向量检索默认返回条数
 * @param similarityThreshold  向量检索相似度阈值（0~1，越大越严格）
 * @param maxContextChars      拼进提示词的上下文字符上限
 * @param chunkSize            文本切块大小（token 数）
 * @param minChunkSizeChars    切块的最小字符数
 * @param minChunkLengthToEmbed 小于该长度的块会被丢弃
 * @param maxNumChunks         单文档最大切块数
 * @param historyWindow        多轮对话带入的历史消息条数
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @DefaultValue("5") Integer topK,
        @DefaultValue("0.35") Double similarityThreshold,
        @DefaultValue("4000") Integer maxContextChars,
        @DefaultValue("500") Integer chunkSize,
        @DefaultValue("50") Integer minChunkSizeChars,
        @DefaultValue("10") Integer minChunkLengthToEmbed,
        @DefaultValue("1000") Integer maxNumChunks,
        @DefaultValue("6") Integer historyWindow) {
}
