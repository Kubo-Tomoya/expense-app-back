package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * パスワード再設定の依頼（ステップ1）のリクエストボディ
 */
@Getter
@Setter
public class PasswordResetRequestDto {

    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;
}