package com.example.expenseapp.exception;

/**
 * 既に登録済みのメールアドレスで、再度ユーザー登録しようとした際に投げる専用例外。
 * 汎用的なRuntimeExceptionのままだと、呼び出し側でこの種類のエラーだけを
 * 狙って処理する（＝適切なHTTPステータスコードに変換する）ことができないため、
 * 意味の伝わる専用クラスとして切り出している
 */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}