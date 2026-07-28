package com.example.expenseapp.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * 入金状況の更新リクエスト（F-19）。
 *
 * 入金状況は2値の切替のみで、paid_atの更新しか副作用がないため、
 * 発行・取消のように専用エンドポイントへ分けず、1本のAPIで状態と入金日を受け取る
 */
@Getter
@Setter
public class InvoicePaymentRequestDto {

    // DB側のCHECK制約と二重チェックの構成にする（F-27のstatusと同じ方針）
    @NotBlank(message = "入金状況は必須です")
    @Pattern(regexp = "unpaid|paid", message = "入金状況は未入金／入金済みのいずれかを指定してください")
    private String paymentStatus;

    /**
     * 入金日。実務では通帳を見て後からまとめて登録するため、
     * 操作日で固定せず入力値を使う（画面側の既定値は当日）。
     * 解除（unpaid）のときは使用しない
     */
    private LocalDate paidAt;

    /**
     * 単一項目では表現できない条件のため、相関チェックとして実装する。
     * 入金日を記録せずに入金済みにできてしまうと、入金日を根拠にした確認ができなくなる
     */
    @AssertTrue(message = "入金済みにする場合は入金日を入力してください")
    public boolean isPaidAtPresentWhenPaid() {
        if (!"paid".equals(paymentStatus)) {
            return true;
        }
        return paidAt != null;
    }

    /**
     * まだ着金していない入金を記録できてしまうため、未来日は許容しない
     */
    @AssertTrue(message = "入金日に未来の日付は指定できません")
    public boolean isPaidAtNotFuture() {
        if (paidAt == null) {
            return true;
        }
        return !paidAt.isAfter(LocalDate.now());
    }
}
