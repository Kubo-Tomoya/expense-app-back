package com.example.expenseapp.dto.response;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * GET /api/expenses/summary/yearly のレスポンス形式。
 * 1年分の集計データを「年間合計」「カテゴリ別合計」「月別の内訳12件」の
 * 3つの切り口でまとめて返す（S-04集計画面が3モードを切り替えても
 * 同じレスポンスの中のデータを使い回せるようにするため）。
 */
@Getter
@Setter
public class YearSummaryResponseDto {

	private Integer year;
	
	/** 年間合計  */
    private Integer totalAmount;
    
    /** カテゴリ別年間合計  */
    private Map<String, Integer> categoryTotals;
    
    /** 各月の内訳 */
    private List<MonthlySummaryResponseDto> monthly;

}
