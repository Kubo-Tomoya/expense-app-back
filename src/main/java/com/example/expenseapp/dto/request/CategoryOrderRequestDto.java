package com.example.expenseapp.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;
import lombok.Setter;

/**
 * カテゴリの並び替えリクエスト（F-28）。
 *
 * 表示したい順にカテゴリIDを並べた配列を受け取り、display_orderを1から振り直す。
 * 1件ずつ更新する方式にすると、途中で失敗した場合に並び順が壊れるため、
 * 配列を1回のリクエストで受け取って一括更新する
 */
@Getter
@Setter
public class CategoryOrderRequestDto {

    @NotEmpty(message = "並び替え対象のカテゴリIDを指定してください")
    private List<Integer> categoryIds;

}
