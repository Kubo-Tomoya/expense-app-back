package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.expenseapp.entity.Expense;

/**
 * TaxCalculatorのテスト（F-21）。
 *
 * 税抜金額・消費税額は列として保持せず算出するため、この算出ロジックが
 * F-21の中心になる。DBにもモックにも依存しないため、素のJUnitで検証する。
 *
 * 単体テスト仕様書F-21のNo.6〜10に対応する。
 */
class TaxCalculatorTest {

    private TaxCalculator taxCalculator;

    @BeforeEach
    void setUp() {
        taxCalculator = new TaxCalculator();
    }

    // F-21 No.6
    @Test
    void 税込金額から税抜金額と消費税額を算出する() {
        long tax = taxCalculator.calculateTax(Expense.TAX_CATEGORY_TAXABLE_10, 11000L);
        long excluding = taxCalculator.calculateExcludingTax(Expense.TAX_CATEGORY_TAXABLE_10, 11000L);

        assertThat(tax).isEqualTo(1000L);
        assertThat(excluding).isEqualTo(10000L);
    }

    // F-21 No.7
    @Test
    void 軽減8パーセントで算出できる() {
        long tax = taxCalculator.calculateTax(Expense.TAX_CATEGORY_TAXABLE_8, 10800L);
        long excluding = taxCalculator.calculateExcludingTax(Expense.TAX_CATEGORY_TAXABLE_8, 10800L);

        assertThat(tax).isEqualTo(800L);
        assertThat(excluding).isEqualTo(10000L);
    }

    // F-21 No.8
    @Test
    void 消費税額の1円未満は切り捨てになる() {
        // 1,000円の10%分は 90.909... 円
        long tax = taxCalculator.calculateTax(Expense.TAX_CATEGORY_TAXABLE_10, 1000L);
        long excluding = taxCalculator.calculateExcludingTax(Expense.TAX_CATEGORY_TAXABLE_10, 1000L);

        assertThat(tax).isEqualTo(90L);
        // 税抜＋消費税が必ず税込に一致する（切り捨て分を税抜側に寄せる）
        assertThat(excluding).isEqualTo(910L);
        assertThat(excluding + tax).isEqualTo(1000L);
    }

    // F-21 No.9
    @Test
    void 区分ごとに合計してから1回だけ計算する() {
        // 税込1,000円の課税10%が3件ある場合
        long perItem = taxCalculator.calculateTax(Expense.TAX_CATEGORY_TAXABLE_10, 1000L);
        long sumOfPerItem = perItem * 3;
        long fromTotal = taxCalculator.calculateTax(Expense.TAX_CATEGORY_TAXABLE_10, 3000L);

        // 明細ごとに端数処理すると270円だが、合計してから計算すると272円になる。
        // 集計に使うのは後者（適格請求書と同じ「税率ごとに1回の端数処理」に揃える）
        assertThat(sumOfPerItem).isEqualTo(270L);
        assertThat(fromTotal).isEqualTo(272L);
    }

    // F-21 No.10
    @Test
    void 非課税と不課税は消費税額が0になる() {
        assertThat(taxCalculator.calculateTax(Expense.TAX_CATEGORY_TAX_EXEMPT, 10000L)).isZero();
        assertThat(taxCalculator.calculateTax(Expense.TAX_CATEGORY_NON_TAXABLE, 10000L)).isZero();

        // 税抜金額は税込金額と同額になる
        assertThat(taxCalculator.calculateExcludingTax(Expense.TAX_CATEGORY_TAX_EXEMPT, 10000L))
            .isEqualTo(10000L);
        assertThat(taxCalculator.calculateExcludingTax(Expense.TAX_CATEGORY_NON_TAXABLE, 10000L))
            .isEqualTo(10000L);
    }

    @Test
    void 区分に対応する税率を返す() {
        assertThat(taxCalculator.taxRateOf(Expense.TAX_CATEGORY_TAXABLE_10)).isEqualTo(10);
        assertThat(taxCalculator.taxRateOf(Expense.TAX_CATEGORY_TAXABLE_8)).isEqualTo(8);
        assertThat(taxCalculator.taxRateOf(Expense.TAX_CATEGORY_TAX_EXEMPT)).isZero();
        assertThat(taxCalculator.taxRateOf(Expense.TAX_CATEGORY_NON_TAXABLE)).isZero();
    }

    @Test
    void 経費1件の消費税額は参考値として算出できる() {
        Expense expense = new Expense();
        expense.setAmount(1100);
        expense.setTaxCategory(Expense.TAX_CATEGORY_TAXABLE_10);

        assertThat(taxCalculator.calculateTaxOf(expense)).isEqualTo(100L);
    }
}
