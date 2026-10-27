package com.example.rag.service;

import com.example.rag.domain.UserAccount;
import com.example.rag.repository.UserAccountRepository;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.AdminUserView;
import com.example.rag.web.dto.UserCreateRequest;
import com.example.rag.web.dto.UserUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 后台用户管理：账号增删改查、启停用与密码重置。
 * 停用、改角色或改密码会同步注销该用户的全部 Redis 会话。
 */
@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final AuthTokenService tokenService;

    public UserService(UserAccountRepository userAccountRepository, AuthTokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> list(String keyword, String role) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String roleFilter = role == null ? "" : role.trim();
        return userAccountRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(user -> roleFilter.isEmpty() || roleFilter.equals(user.getRole()))
                .filter(user -> key.isEmpty()
                        || user.getAccount().toLowerCase(Locale.ROOT).contains(key)
                        || user.getName().toLowerCase(Locale.ROOT).contains(key)
                        || user.getTitle().toLowerCase(Locale.ROOT).contains(key))
                .map(AdminUserView::from)
                .toList();
    }

    @Transactional
    public AdminUserView create(UserCreateRequest request) {
        String account = normalizeAccount(request.account());
        if (userAccountRepository.findByAccountIgnoreCase(account).isPresent()) {
            throw new IllegalArgumentException("账号「" + account + "」已存在");
        }
        UserAccount user = new UserAccount(
                account,
                request.password(),
                request.name().trim(),
                request.title().trim(),
                normalizeRole(request.role())
        );
        user.setEnabled(request.enabled() == null || request.enabled());
        return AdminUserView.from(userAccountRepository.save(user));
    }

    @Transactional
    public AdminUserView update(Long id, UserUpdateRequest request, UserAccount operator, String authorization) {
        UserAccount user = find(id);
        String account = normalizeAccount(request.account());
        userAccountRepository.findByAccountIgnoreCase(account)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("账号「" + account + "」已存在");
                });

        String role = normalizeRole(request.role());
        boolean enabled = request.enabled() == null ? user.isEnabled() : request.enabled();
        boolean roleChanged = !role.equals(user.getRole());
        boolean disabled = user.isEnabled() && !enabled;

        if (user.getId().equals(operator.getId())) {
            if (disabled) {
                throw new IllegalArgumentException("不能停用自己的账号");
            }
            if (roleChanged && !UserAccount.ROLE_BACK.equals(role)) {
                throw new IllegalArgumentException("不能移除自己的后台权限");
            }
        }

        String password = request.password();
        boolean passwordChanged = password != null && !password.isBlank();
        if (passwordChanged && password.trim().length() < 6) {
            throw new IllegalArgumentException("密码长度需为 6-64 位");
        }

        user.setAccount(account);
        user.setName(request.name().trim());
        user.setTitle(request.title().trim());
        user.setRole(role);
        user.setEnabled(enabled);
        if (passwordChanged) {
            user.setPassword(password.trim());
        }
        AdminUserView view = AdminUserView.from(userAccountRepository.save(user));

        if (disabled || roleChanged) {
            tokenService.revokeAllForUser(user.getId());
        } else if (passwordChanged) {
            // 管理员改自己的密码时保留当前会话，改别人的密码则让所有设备退出
            if (user.getId().equals(operator.getId())) {
                tokenService.revokeOtherSessions(user.getId(), authorization);
            } else {
                tokenService.revokeAllForUser(user.getId());
            }
        }
        return view;
    }

    @Transactional
    public AdminUserView changeStatus(Long id, boolean enabled, UserAccount operator) {
        UserAccount user = find(id);
        if (user.getId().equals(operator.getId()) && !enabled) {
            throw new IllegalArgumentException("不能停用自己的账号");
        }
        user.setEnabled(enabled);
        AdminUserView view = AdminUserView.from(userAccountRepository.save(user));
        if (!enabled) {
            tokenService.revokeAllForUser(user.getId());
        }
        return view;
    }

    @Transactional
    public void delete(Long id, UserAccount operator) {
        UserAccount user = find(id);
        if (user.getId().equals(operator.getId())) {
            throw new IllegalArgumentException("不能删除自己的账号");
        }
        if (UserAccount.ROLE_BACK.equals(user.getRole())
                && user.isEnabled()
                && userAccountRepository.countByRoleAndEnabled(UserAccount.ROLE_BACK, true) <= 1) {
            throw new IllegalArgumentException("至少保留一个可用的后台管理员账号");
        }
        tokenService.revokeAllForUser(user.getId());
        userAccountRepository.delete(user);
    }

    private UserAccount find(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在或已被删除"));
    }

    private String normalizeAccount(String account) {
        return account == null ? "" : account.trim();
    }

    private String normalizeRole(String role) {
        if (!UserAccount.ROLE_FRONT.equals(role) && !UserAccount.ROLE_BACK.equals(role)) {
            throw new IllegalArgumentException("角色只能是 front 或 back");
        }
        return role;
    }
}
