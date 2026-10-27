package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通用 AI 配置：把自动装配出来的 {@link ChatClient.Builder} 变成一个单例 ChatClient。
 */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
