package com.example.expenseapp.exception;

/**
 * 無効化済み（is_active = false）の取引先を、新しい請求書の宛先に指定した場合に投げる専用例外。
 *
 * ResourceNotFoundException（404）ではなく専用の400としているのは、
 * 「存在しない・他人のもの」と「存在するが無効化されている」は利用者にとって別の状況であり、
 * 後者は「有効な取引先を選び直す」という具体的な対処ができるため。
 * 存在推測のリスクも無い（自分が無効化した取引先であることは本人が知っている）
 */
public class InactiveClientException extends RuntimeException {
    public InactiveClientException(String message) {
        super(message);
    }
}
