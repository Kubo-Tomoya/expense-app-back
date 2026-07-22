package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 事業者プロフィール登録・更新（PUT /api/business-profile）のリクエストボディ。
 * 初回登録も2回目以降の更新も、同じ形式（Upsert）で扱う
 */
@Getter
@Setter
public class BusinessProfileRequestDto {

    @Size(max = 100, message = "屋号は100文字以内で入力してください")
    private String businessName; // 任意

    @NotBlank(message = "氏名は必須です")
    @Size(max = 100, message = "氏名は100文字以内で入力してください")
    private String ownerName;

    @Size(max = 255, message = "住所は255文字以内で入力してください")
    private String address; // 任意

    // 任意項目のため@NotBlankは付けない。
    // 入力された場合のみ「T」+13桁の数字という形式を強制する。
    // 空文字列(未入力)もOKにするため、正規表現側で許容している
    @Pattern(regexp = "^(T\\d{13})?$", message = "インボイス登録番号は「T」+13桁の数字で入力してください")
    private String invoiceRegistrationNumber;
}