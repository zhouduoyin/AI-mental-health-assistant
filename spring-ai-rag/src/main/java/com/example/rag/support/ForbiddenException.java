package com.example.rag.support;

/**
 * 已登录但角色不满足要求，映射为 403。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
