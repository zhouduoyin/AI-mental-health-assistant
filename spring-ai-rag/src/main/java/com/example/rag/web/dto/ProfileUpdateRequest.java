package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "请输入姓名")
        @Size(max = 64, message = "姓名不能超过 64 个字符")
        String name,

        @NotBlank(message = "请输入身份")
        @Size(max = 64, message = "身份不能超过 64 个字符")
        String title
) {
}
