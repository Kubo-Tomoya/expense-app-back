package com.example.expenseapp.exception;

/**
 * 無効化済み（is_active = false）のカテゴリを経費に指定した場合に投げる専用例外（F-28）。
 *
 * ResourceNotFoundException（404）ではなく専用の400としているのは、
 * 「存在しない・他人のもの」と「存在するが無効化されている」は利用者にとって別の状況であり、
 * 後者は「有効なカテゴリを選び直す」という具体的な対処ができるため。
 *
 * 取引先のInactiveClientException（F-17）と同じ設計思想。
 * 無効化されたマスタは新規の参照を拒否し、既存の参照は維持する
 */
public class InactiveCategoryException extends RuntimeException {
    public InactiveCategoryException(String message) {
        super(message);
    }
}
