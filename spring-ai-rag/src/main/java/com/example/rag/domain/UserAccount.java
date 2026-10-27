package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 登录账号。角色用 front / back 区分前台来访者与后台运营。
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    public static final String ROLE_FRONT = "front";
    public static final String ROLE_BACK = "back";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String account;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 64)
    private String title;

    @Column(nullable = false, length = 16)
    private String role;

    /** 后台可停用账号；停用后已签发的令牌会在 Redis 中一并失效。 */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean enabled = true;

    @Column
    private Instant lastLoginAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String account, String password, String name, String title, String role) {
        this.account = account;
        this.password = password;
        this.name = name;
        this.title = title;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
