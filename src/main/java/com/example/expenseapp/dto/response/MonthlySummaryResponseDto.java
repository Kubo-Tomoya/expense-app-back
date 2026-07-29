package com.example.expenseapp.dto.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * ある1ヶ月分の集計データ。
 * 例：7月・合計30,000円・内訳{交通費:20,000円, 食費:10,000円}
 *
 * YearSummaryResponseDto の monthly フィールド（12件のリスト）の1要素として使う。
 */
@Getter
@Setter
@AllArgsConstructor
public class MonthlySummaryResponseDto {

	private Integer month;
	
	/** 月の合計金額 */
    private Integer totalAmount;
    
    /** 月のカテゴリ別内訳 */ 
    private Map<String, Integer> categoryBreakdown;

}
