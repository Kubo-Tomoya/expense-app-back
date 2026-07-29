package com.example.expenseapp.exception;

/**
 * アップロードされたファイルが、形式・サイズ等の要件を満たさない場合に投げる専用例外。
 * 「リソースが存在しない」ことが原因ではなく、「送られてきた内容そのものが不正」であるため、
 * ResourceNotFoundExceptionとは意味的に区別する
 */
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}