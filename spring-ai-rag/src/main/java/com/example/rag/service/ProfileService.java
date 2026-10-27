package com.example.rag.service;

import com.example.rag.domain.UserAccount;
import com.example.rag.repository.ConsultationRepository;
import com.example.rag.repository.MoodLogRepository;
import com.example.rag.repository.UserAccountRepository;
import com.example.rag.web.dto.PasswordChangeRequest;
import com.example.rag.web.dto.ProfileUpdateRequest;
import com.example.rag.web.dto.ProfileView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前登录用户查看与维护自己的资料、密码。 */
@Service
public class ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final MoodLogRepository moodLogRepository;
    private final ConsultationRepository consultationRepository;
    private final AuthTokenService tokenService;

    public ProfileService(UserAccountRepository userAccountRepository,
                          MoodLogRepository moodLogRepository,
                          ConsultationRepository consultationRepository,
                          AuthTokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.moodLogRepository = moodLogRepository;
        this.consultationRepository = consultationRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public ProfileView get(UserAccount user) {
        return toView(user);
    }

    @Transactional
    public ProfileView update(UserAccount user, ProfileUpdateRequest request) {
        user.setName(request.name().trim());
        user.setTitle(request.title().trim());
        return toView(userAccountRepository.save(user));
    }

    /**
     * 修改密码后注销该账号在其他设备上的登录态，当前设备保持登录。
     */
    @Transactional
    public void changePassword(UserAccount user, PasswordChangeRequest request, String authorization) {
        if (!user.getPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        String newPassword = request.newPassword().trim();
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度需为 6-64 位");
        }
        if (newPassword.equals(user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        user.setPassword(newPassword);
        userAccountRepository.save(user);
        tokenService.revokeOtherSessions(user.getId(), authorization);
    }

    private ProfileView toView(UserAccount user) {
        long moodCount = moodLogRepository.countByUserId(user.getId());
        long consultationCount = consultationRepository.countByUserName(user.getName());
        return ProfileView.from(user, moodCount, consultationCount);
    }
}
