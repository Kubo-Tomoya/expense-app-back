package com.example.expenseapp.service;

import org.springframework.stereotype.Component;

import com.example.expenseapp.entity.Expense;

/**
 * 税込金額と消費税区分から、税抜金額・消費税額を算出する（F-21）。
 *
 * 経費は税込で入力・保持し、税抜金額と消費税額は列として持たない。
 * F-20で集計金額を税込に統一したため、同じ金額を2系統で管理すると
 * どちらが正かが曖昧になり、税率改定や区分の訂正時に再計算漏れが起きるため。
 *
 * 端数処理は請求書側（InvoiceService）と揃え、
 * 「税率区分ごとに税込金額を合計してから1回だけ計算し、1円未満は切り捨て」とする。
 * 明細ごとに逆算して積み上げると、区分合計に対して計算した額とずれるため、
 * 集計に使う値は必ず {@link #calculateTax(String, long)} に区分合計を渡して求める。
 */
@Component
public class TaxCalculator {

    /**
     * 区分に対応する税率（%）を返す。非課税・不課税は0
     */
    public int taxRateOf(String taxCategory) {
        if (Expense.TAX_CATEGORY_TAXABLE_10.equals(taxCategory)) return 10;
        if (Expense.TAX_CATEGORY_TAXABLE_8.equals(taxCategory)) return 8;
        return 0;
    }

    /**
     * 税込合計から消費税額を算出する（1円未満切り捨て）。
     *
     * @param taxCategory        消費税区分
     * @param totalIncludingTax  同一区分の税込金額の合計
     * @return 消費税額。非課税・不課税は0
     */
    public long calculateTax(String taxCategory, long totalIncludingTax) {
        int rate = taxRateOf(taxCategory);
        if (rate == 0) return 0L;
        // 税込金額から税額を逆算する。整数除算により1円未満は切り捨てられる
        return totalIncludingTax * rate / (100 + rate);
    }

    /**
     * 税込合計から税抜金額を算出する。
     * 税抜金額 = 税込金額 − 消費税額 とすることで、両者の合計が必ず税込金額に一致する
     */
    public long calculateExcludingTax(String taxCategory, long totalIncludingTax) {
        return totalIncludingTax - calculateTax(taxCategory, totalIncludingTax);
    }

    /**
     * 経費1件の消費税額（参考値）。
     *
     * 1件ごとに端数処理するため、複数件の合計は
     * 区分合計に対して算出した値と一致しない場合がある。
     * 申告に使う集計値には使わず、明細の表示・出力（F-23の出力①）に限って使う
     */
    public long calculateTaxOf(Expense expense) {
        return calculateTax(expense.getTaxCategory(), expense.getAmount());
    }
}
