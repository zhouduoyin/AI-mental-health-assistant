package com.example.rag.web.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull(message = "请选择启用状态") Boolean enabled) {
}
