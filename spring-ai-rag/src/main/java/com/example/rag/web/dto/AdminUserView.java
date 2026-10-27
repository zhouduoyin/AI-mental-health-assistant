package com.example.rag.web.dto;

import com.example.rag.domain.UserAccount;

/** 后台用户管理列表行。 */
public record AdminUserView(
        Long id,
        String account,
        String name,
        String title,
        String role,
        boolean enabled,
        Long createdAt,
        Long lastLoginAt
) {

    public static AdminUserView from(UserAccount user) {
        return new AdminUserView(
                user.getId(),
                user.getAccount(),
                user.getName(),
                user.getTitle(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toEpochMilli(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toEpochMilli()
        );
    }
}
