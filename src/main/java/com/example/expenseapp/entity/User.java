package com.example.expenseapp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * usersテーブルに対応するEntity。
 * 1レコード＝1ユーザーアカウントを表す。
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
	
	/** ユーザーID */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; 

	/** ログインID兼メールアドレス */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email; 

    // BCryptでハッシュ化した値のみを保持する。平文パスワードは一切保存しない。
    // 変数名を「password」ではなく「passwordHash」にすることで、
    // 平文を誤って代入するミスを防ぐ意図がある
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** 登録日時（更新不可）*/
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; 

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt; 


}
