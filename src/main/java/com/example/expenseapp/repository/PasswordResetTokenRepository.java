package com.example.expenseapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.expenseapp.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {

    // メール内のリンクをクリックした際、トークン文字列からレコードを検索するために使用
    Optional<PasswordResetToken> findByToken(String token);
}