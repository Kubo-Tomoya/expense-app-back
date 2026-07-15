package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * ログイン（POST /api/auth/login）のリクエストボディ。
 * 登録時と異なり、ここでは文字数等のバリデーションは行わない
 * （正しいパスワードでも意図せずエラーになるのを防ぐため）
 */
@Getter
@Setter
public class LoginRequestDto {

	@NotBlank(message = "メールアドレスは必須です")
    private String email;

    @NotBlank(message = "パスワードは必須です")
    private String password;
    
}
