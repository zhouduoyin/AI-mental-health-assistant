package com.example.rag.web;

import com.example.rag.domain.UserAccount;
import com.example.rag.service.AuthTokenService;
import com.example.rag.service.ProfileService;
import com.example.rag.web.dto.PasswordChangeRequest;
import com.example.rag.web.dto.ProfileUpdateRequest;
import com.example.rag.web.dto.ProfileView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthTokenService tokenService;

    public ProfileController(ProfileService profileService, AuthTokenService tokenService) {
        this.profileService = profileService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ProfileView get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserAccount user = tokenService.require(authorization);
        return profileService.get(user);
    }

    @PutMapping
    public ProfileView update(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @Valid @RequestBody ProfileUpdateRequest request) {
        UserAccount user = tokenService.require(authorization);
        return profileService.update(user, request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @Valid @RequestBody PasswordChangeRequest request) {
        UserAccount user = tokenService.require(authorization);
        profileService.changePassword(user, request, authorization);
    }
}
