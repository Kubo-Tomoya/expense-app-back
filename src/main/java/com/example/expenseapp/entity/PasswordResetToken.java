package com.example.expenseapp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * password_reset_tokensテーブルに対応するEntity。
 * 1レコード＝1回のパスワード再設定依頼を表す使い捨てトークン。
 *
 * usedAtを持たせている理由：
 * 同じトークンが2回使われる（例：メールのリンクを2回クリックする）ことを防ぐため。
 * 1回使われたら used_at に日時をセットし、以降は無効なトークンとして扱う
 */
@Getter
@Setter
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // どのユーザーの再設定依頼か

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token; // メールに埋め込む使い捨てトークン文字列

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // この日時を過ぎたら無効

    @Column(name = "used_at")
    private LocalDateTime usedAt; // 使用済みになった日時（NULLなら未使用）

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}