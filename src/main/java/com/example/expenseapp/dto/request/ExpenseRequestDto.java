package com.example.expenseapp.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseRequestDto {
	
	@NotBlank(message = "タイトルは必須です")
    @Size(max = 100, message = "タイトルは100文字以内で入力してください")
    private String title;

    @NotNull(message = "金額は必須です")
    @Min(value = 1, message = "金額は1円以上で入力してください")
    private Integer amount;

    @NotNull(message = "カテゴリは必須です")
    private Integer categoryId;

    @NotNull(message = "日付は必須です")
    private LocalDate expenseDate;

    @Size(max = 500, message = "メモは500文字以内で入力してください")
    private String memo;

    private String status = "registered";

}
