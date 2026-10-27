package com.example.rag.support;

/**
 * 未登录或令牌失效，映射为 401。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
