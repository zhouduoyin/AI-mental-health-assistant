package com.example.rag.web.dto;

import com.example.rag.domain.UserAccount;

public record AuthUserView(
        Long id,
        String account,
        String name,
        String title,
        String role
) {

    public static AuthUserView from(UserAccount user) {
        return new AuthUserView(
                user.getId(),
                user.getAccount(),
                user.getName(),
                user.getTitle(),
                user.getRole()
        );
    }
}
