package com.example.expenseapp.dto.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SummaryResponseDto {

	private Integer year;
    private Integer month;
    private Integer totalAmount;
    private Map<String, Integer> categoryBreakdown;

    // 下書き分の金額合計（F-27）。
    // ダッシュボードの「予測モード」で、確定分totalAmountに加算して表示するため、
    // また「うち下書き分」の内訳表示のために別フィールドとして保持する
    private Integer draftAmount;
}