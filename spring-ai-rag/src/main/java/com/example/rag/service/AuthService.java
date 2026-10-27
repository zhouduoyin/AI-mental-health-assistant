package com.example.rag.service;

import com.example.rag.domain.UserAccount;
import com.example.rag.repository.UserAccountRepository;
import com.example.rag.support.UnauthorizedException;
import com.example.rag.web.dto.AuthUserView;
import com.example.rag.web.dto.LoginResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final AuthTokenService tokenService;

    public AuthService(UserAccountRepository userAccountRepository, AuthTokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.tokenService = tokenService;
    }

    public LoginResponse login(String account, String password) {
        UserAccount user = userAccountRepository.findByAccountIgnoreCase(account == null ? "" : account.trim())
                .orElseThrow(() -> new UnauthorizedException("账号或密码不正确"));
        if (!user.getPassword().equals(password)) {
            throw new UnauthorizedException("账号或密码不正确");
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("账号已停用，请联系管理员");
        }
        user.setLastLoginAt(Instant.now());
        userAccountRepository.save(user);
        return new LoginResponse(tokenService.issue(user), AuthUserView.from(user));
    }

    public AuthUserView me(String authorization) {
        return AuthUserView.from(tokenService.require(authorization));
    }

    public void logout(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        tokenService.revoke(value);
    }
}
