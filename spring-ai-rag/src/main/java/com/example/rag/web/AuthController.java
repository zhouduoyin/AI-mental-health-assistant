package com.example.rag.web;

import com.example.rag.service.AuthService;
import com.example.rag.web.dto.AuthUserView;
import com.example.rag.web.dto.LoginRequest;
import com.example.rag.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "登录鉴权", description = "账号密码登录、当前用户、退出登录")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "账号密码登录")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.account(), request.password());
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户")
    public AuthUserView me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "退出登录")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
    }
}
