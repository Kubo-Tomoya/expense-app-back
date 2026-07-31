package com.example.expenseapp.exception;

/**
 * 同一ユーザー内に同名のカテゴリを登録・更新しようとした場合に投げる専用例外（F-28）。
 *
 * DuplicateEmailException（409）ではなく400としているのは、
 * メールアドレスの重複が「他人が既に使っている」という自分では解消できない状況である一方、
 * カテゴリ名の重複は「自分の別のカテゴリと同名」であり、名前を変えればすぐ解消できる
 * 入力の誤りに近いため。
 *
 * 一意制約違反（DataIntegrityViolationException）のままにするとSQL例外由来の
 * 分かりにくいメッセージになるため、Service側で事前に確認して本例外を投げる
 */
public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException(String message) {
        super(message);
    }
}
