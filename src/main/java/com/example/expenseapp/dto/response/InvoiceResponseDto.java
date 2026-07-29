package com.example.expenseapp.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 請求書のレスポンス。
 *
 * clientName等は「発行済みなら発行時のスナップショット、下書きなら現在の取引先の値」を返す。
 * 画面側が状態によって参照先を切り替えなくて済むようにするため。
 *
 * itemsは一覧（GET /api/invoices）ではnull、詳細（GET /api/invoices/{id}）では明細を返す。
 * 一覧で全請求書の明細まで返すとペイロードが不必要に大きくなるため（S-13の設計）
 */
@Getter
@AllArgsConstructor
public class InvoiceResponseDto {
    private Integer id;
    private String invoiceNumber; // 下書きの間はnull（未採番）
    private Integer clientId;
    private String clientName;
    private String clientHonorific;
    private String clientAddress;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String status;        // draft / issued / canceled
    private Integer subtotalAmount;
    private Integer taxAmount;
    private Integer totalAmount;
    private List<InvoiceTaxSummaryDto> taxSummaries;
    private List<InvoiceItemResponseDto> items;

    // 発行者情報（F-15 事業者プロフィールのスナップショット。下書きの間はnull）
    private String issuerBusinessName;
    private String issuerOwnerName;
    private String issuerAddress;
    private String issuerInvoiceRegistrationNumber;

    // 入金管理（F-19で使用）
    private String paymentStatus; // unpaid / paid
    private LocalDateTime paidAt;

    private LocalDateTime issuedAt;
    private LocalDateTime canceledAt;
    private String canceledReason;
    private LocalDateTime createdAt;
}
