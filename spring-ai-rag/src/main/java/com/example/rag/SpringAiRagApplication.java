package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring AI RAG 知识库示例应用。
 *
 * <p>技术栈：Spring Boot 3.5 + Spring AI 1.1 + Milvus（向量库）+ MySQL（业务库）
 * + OpenAI 兼容网关（对话模型 deepseek-v4-flash）。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiRagApplication.class, args);
    }
}
