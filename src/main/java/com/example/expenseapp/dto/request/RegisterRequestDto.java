package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * ユーザー登録（POST /api/auth/register）のリクエストボディ
 */
@Getter
@Setter
public class RegisterRequestDto {

	@NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    // 8文字以上を必須とする。ログイン時と違い、新規登録時のみ強度チェックを行う
    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String password;
}
