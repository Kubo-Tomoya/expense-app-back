package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * パスワード再設定の確定（ステップ2）のリクエストボディ。
 * メール内リンクのトークンと、新しいパスワードをセットで受け取る
 */
@Getter
@Setter
public class PasswordResetConfirmDto {

    @NotBlank(message = "トークンは必須です")
    private String token;

    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String newPassword;
}