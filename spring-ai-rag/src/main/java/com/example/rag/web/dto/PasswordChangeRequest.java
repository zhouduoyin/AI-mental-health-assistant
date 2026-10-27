package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "请输入当前密码")
        String currentPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位")
        String newPassword
) {
}
