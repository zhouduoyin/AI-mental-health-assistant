package com.example.rag.support;

/**
 * 业务资源不存在，统一映射为 HTTP 404。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
