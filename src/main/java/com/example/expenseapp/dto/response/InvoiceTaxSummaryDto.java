package com.example.expenseapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 税率区分ごとの内訳。適格請求書では税率ごとの小計・消費税額の記載が必須のため、
 * 画面・PDF（F-18）が個別に集計しなくて済むようレスポンスに含める。
 *
 * 消費税額は「税率区分ごとに明細を合計した後、1回だけ計算して1円未満を切り捨てた額」
 */
@Getter
@AllArgsConstructor
public class InvoiceTaxSummaryDto {
    private String taxCategory;      // taxable_10 / taxable_8 / tax_exempt
    private Integer taxRate;         // 10 / 8 / 0
    private Integer subtotalAmount;  // その税率区分の税抜合計
    private Integer taxAmount;       // その税率区分の消費税額
}
