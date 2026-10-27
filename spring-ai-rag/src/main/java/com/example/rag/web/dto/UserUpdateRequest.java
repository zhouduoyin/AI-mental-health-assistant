package com.example.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "请输入账号")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,31}$",
                message = "账号需以字母开头，由 3-32 位字母、数字或下划线组成")
        String account,

        /** 留空表示不修改密码。 */
        @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
        String password,

        @NotBlank(message = "请输入姓名")
        @Size(max = 64, message = "姓名不能超过 64 个字符")
        String name,

        @NotBlank(message = "请输入身份")
        @Size(max = 64, message = "身份不能超过 64 个字符")
        String title,

        @NotBlank(message = "请选择角色")
        @Pattern(regexp = "front|back", message = "角色只能是 front 或 back")
        String role,

        Boolean enabled
) {
}
