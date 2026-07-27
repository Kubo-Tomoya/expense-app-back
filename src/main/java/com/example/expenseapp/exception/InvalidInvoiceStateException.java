package com.example.expenseapp.exception;

/**
 * 請求書のステータスとして許されない操作を行った場合に投げる専用例外。
 *
 * 具体例：
 * ・発行済み（issued）の請求書を編集・再発行・削除しようとした
 * ・下書き（draft）の請求書を取消しようとした
 *
 * 適格請求書は発行時点の記載事項を保持する必要があるため、
 * 発行後の編集は「できない」ことを明示的なエラー（400）として返す。
 * 訂正は取消＋再発行で行う
 */
public class InvalidInvoiceStateException extends RuntimeException {
    public InvalidInvoiceStateException(String message) {
        super(message);
    }
}
