package com.example.expenseapp.exception;

/**
 * パスワード再設定のトークンが「存在しない」「期限切れ」「使用済み」のいずれかに
 * 該当した場合に投げる専用例外
 */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException(String message) {
        super(message);
    }
}