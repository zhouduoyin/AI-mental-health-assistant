package com.example.rag.web.dto;

import com.example.rag.domain.UserAccount;

/** 当前登录用户的个人资料。 */
public record ProfileView(
        Long id,
        String account,
        String name,
        String title,
        String role,
        boolean enabled,
        Long createdAt,
        Long lastLoginAt,
        long moodCount,
        long consultationCount
) {

    public static ProfileView from(UserAccount user, long moodCount, long consultationCount) {
        return new ProfileView(
                user.getId(),
                user.getAccount(),
                user.getName(),
                user.getTitle(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toEpochMilli(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toEpochMilli(),
                moodCount,
                consultationCount
        );
    }
}
