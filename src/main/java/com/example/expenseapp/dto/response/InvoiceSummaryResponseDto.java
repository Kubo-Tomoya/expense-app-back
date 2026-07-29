package com.example.expenseapp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 収支ダッシュボード用の請求書集計（F-20）。
 *
 * 金額はいずれも税込。売上は発行日ベースで発行済み（issued）のみを対象とし、
 * 下書き・取消済みは含まない。
 *
 * 収支（売上−経費）は経費側の集計と組み合わせて算出するため、
 * このDTOには含めずフロント側で計算する
 */
@Getter
@AllArgsConstructor
public class InvoiceSummaryResponseDto {

    /** 指定月の売上（発生主義・税込） */
    private Integer salesAmount;

    /**
     * 未回収金額（発行済みかつ未入金）。
     * 債権残高であり月次のフローではないため、月では絞らず全期間を対象とする
     */
    private Integer unpaidAmount;

    /** 未回収のうち支払期日を過ぎたもの */
    private Integer overdueAmount;

    /** 指定年の1〜12月の売上（データが無い月は0）。S-04の収支推移モードで使用する */
    private List<Integer> monthlySales;
}
