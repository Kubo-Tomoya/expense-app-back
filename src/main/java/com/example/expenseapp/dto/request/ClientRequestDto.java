package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientRequestDto {

    @NotBlank(message = "会社名／屋号は必須です")
    @Size(max = 100, message = "会社名／屋号は100文字以内で入力してください")
    private String name;

    // デフォルト「御中」。個人の取引先の場合は画面側で「様」に変更する運用を想定
    @NotBlank(message = "敬称は必須です")
    private String honorific = "御中";

    @Size(max = 100, message = "担当者名は100文字以内で入力してください")
    private String contactPerson;

    @Size(max = 255, message = "住所は255文字以内で入力してください")
    private String address;

    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    @Size(max = 20, message = "電話番号は20文字以内で入力してください")
    private String phone;

    private String memo;
}