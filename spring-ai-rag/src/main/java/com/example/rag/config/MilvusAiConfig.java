package com.example.rag.config;

import com.example.rag.support.HashingEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Milvus 模式（profile=milvus）只需要本地 Embedding：
 * VectorStore 由 Spring AI 的 Milvus 自动装配创建，这里不能再声明 VectorStore Bean。
 */
@Configuration(proxyBeanMethods = false)
@Profile("milvus")
public class MilvusAiConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel(1024);
    }
}
