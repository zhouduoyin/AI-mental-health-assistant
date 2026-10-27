package com.example.rag.support;

/**
 * 调用大模型失败，统一映射为 HTTP 502。
 */
public class AiInvocationException extends RuntimeException {

    public AiInvocationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiInvocationException(String message) {
        super(message);
    }
}
