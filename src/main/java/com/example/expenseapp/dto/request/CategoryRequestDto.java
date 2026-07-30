package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * カテゴリの登録・更新リクエスト（F-28）。
 *
 * display_orderは画面から直接入力させず、並び替え操作（PUT /api/categories/order）で
 * 更新する。連番の整合をサーバー側で保つため
 */
@Getter
@Setter
public class CategoryRequestDto {

    // プルダウンの表示幅に収まる長さに制限する
    @NotBlank(message = "カテゴリ名は必須です")
    @Size(max = 20, message = "カテゴリ名は20文字以内で入力してください")
    private String name;

    // 確定申告の勘定科目名。選択肢からの選択と自由入力の併用のため、値は制限せず長さのみ確認する
    @Size(max = 50, message = "確定申告の勘定科目は50文字以内で入力してください")
    private String taxFormCategory;

}
