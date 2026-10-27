package com.example.rag.web.dto;

public record LoginResponse(String token, AuthUserView user) {
}
