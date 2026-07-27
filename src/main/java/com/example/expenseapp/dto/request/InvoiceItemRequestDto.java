package com.example.expenseapp.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 請求書明細1行分のリクエスト。
 * 金額（amount）と税率（taxRate）はサーバー側で算出するため、リクエストには含めない
 * （フロントの計算結果を信用せず、保存値は必ずサーバー計算値を正とする方針）
 */
@Getter
@Setter
public class InvoiceItemRequestDto {

    @NotBlank(message = "品目名は必須です")
    @Size(max = 100, message = "品目名は100文字以内で入力してください")
    private String description;

    // 0.5人日のような小数の数量を許容するため、BigDecimalで受け取る
    @NotNull(message = "数量は必須です")
    @DecimalMin(value = "0.01", message = "数量は0より大きい値を入力してください")
    private BigDecimal quantity;

    @NotNull(message = "税抜単価は必須です")
    @Min(value = 0, message = "税抜単価は0以上で入力してください")
    private Integer unitPrice;

    // DB側のCHECK制約と二重チェックの構成にする（F-27のstatusと同じ方針）
    @NotBlank(message = "税率区分は必須です")
    @Pattern(regexp = "taxable_10|taxable_8|tax_exempt",
             message = "税率区分は課税10%／軽減8%／非課税のいずれかを指定してください")
    private String taxCategory = "taxable_10";
}
