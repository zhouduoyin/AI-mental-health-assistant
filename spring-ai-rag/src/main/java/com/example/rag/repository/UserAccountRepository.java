package com.example.rag.repository;

import com.example.rag.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByAccountIgnoreCase(String account);

    long countByRole(String role);

    long countByRoleAndEnabled(String role, boolean enabled);
}
