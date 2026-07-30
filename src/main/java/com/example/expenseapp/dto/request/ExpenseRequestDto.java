package com.example.expenseapp.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    // DBのCHECK制約（registered/draftのみ許容）と同じ内容を、
    // アプリ側でも二重にチェックする。DB制約だけに頼ると、
    // 違反時のエラーがSQL例外のまま表面化し分かりにくいメッセージになるため
    @Pattern(regexp = "^(registered|draft)$", message = "ステータスはregisteredまたはdraftのいずれかを指定してください")
    private String status = "registered";

    // F-21：消費税区分。statusと同じく、DBのCHECK制約と同じ内容をアプリ側でも二重にチェックする
    @Pattern(regexp = "^(taxable_10|taxable_8|tax_exempt|non_taxable)$",
        message = "消費税区分はtaxable_10・taxable_8・tax_exempt・non_taxableのいずれかを指定してください")
    private String taxCategory = "taxable_10";

    // F-22：受領した領収書が適格請求書かどうか。
    // 課税区分以外を指定された場合はService側でnullに落とすため、ここでは値を制限しない
    private Boolean isQualifiedInvoice;

    // F-22：支払先のインボイス登録番号。任意項目で、入力があるときのみ形式をチェックする。
    // 空文字も許容するのは、フォームで一度入力してから消した場合に空文字が送られてくるため
    @Pattern(regexp = "^$|^T\\d{13}$",
        message = "インボイス登録番号は「T」＋数字13桁で入力してください")
    private String vendorRegistrationNumber;

}