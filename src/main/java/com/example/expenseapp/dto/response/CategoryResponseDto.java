package com.example.expenseapp.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryResponseDto {

	private Integer id;
    private String name;
    private Integer displayOrder;

    /** 確定申告の勘定科目名（F-28）。未設定はnullで、F-23の集計では「雑費」として扱う */
    private String taxFormCategory;

    /** 有効フラグ（F-28）。falseの場合は経費登録時のプルダウンに表示しない */
    private Boolean isActive;

    /**
     * このカテゴリを使う経費の件数（F-28）。
     * 無効化の影響を判断するためにS-15の一覧で表示する。
     * 論理削除済みの経費は含まない
     */
    private Long usageCount;

}
