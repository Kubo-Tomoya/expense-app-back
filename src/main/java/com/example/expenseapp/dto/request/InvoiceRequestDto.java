package com.example.expenseapp.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * 請求書の作成・更新リクエスト。
 *
 * invoiceNumber・status・各金額はリクエストに含めない。
 * 番号は発行API（PUT /api/invoices/{id}/issue）で採番し、
 * 金額はサーバー側で明細から再計算するため
 */
@Getter
@Setter
public class InvoiceRequestDto {

    @NotNull(message = "取引先は必須です")
    private Integer clientId;

    @NotNull(message = "発行日は必須です")
    private LocalDate issueDate;

    // 未指定の場合はサーバー側で「発行日の翌月末」を設定する（S-14の既定値仕様）
    private LocalDate dueDate;

    @NotEmpty(message = "明細は1行以上入力してください")
    @Valid
    private List<InvoiceItemRequestDto> items;
}
