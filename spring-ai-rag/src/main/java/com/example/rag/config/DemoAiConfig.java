package com.example.rag.config;

import com.example.rag.support.HashingEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 无需外部依赖的本地运行配置，demo / local 两个 profile 共用：
 *
 * <ul>
 *   <li>向量库：SimpleVectorStore（JVM 内存）代替 Milvus</li>
 *   <li>Embedding：本地哈希模型代替在线 Embedding 接口</li>
 * </ul>
 *
 * demo 用 H2 内存库；local 用本机 MySQL 持久化，见 application-local.yml。
 */
@Configuration(proxyBeanMethods = false)
@Profile({"demo", "local"})
public class DemoAiConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel(1024);
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
