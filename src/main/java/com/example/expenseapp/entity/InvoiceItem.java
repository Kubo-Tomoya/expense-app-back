package com.example.expenseapp.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * invoice_itemsテーブルに対応するEntity。F-17（請求書作成）で新規作成。
 *
 * unit_priceは税抜単価。請求書は税抜単価＋消費税の記載が一般的で、
 * 税率別内訳の算出基準も税抜であるため（F-21の経費側が税込入力なのとは逆）。
 *
 * tax_categoryとtax_rateの両方を持つのは、区分名をF-21/F-23とのマッピングに使い、
 * tax_rateには発行時点の税率をスナップショットとして残すため
 */
@Getter
@Setter
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    // 税率区分と、それに対応する税率（％）
    public static final String TAX_CATEGORY_TAXABLE_10 = "taxable_10";
    public static final String TAX_CATEGORY_TAXABLE_8 = "taxable_8";
    public static final String TAX_CATEGORY_TAX_EXEMPT = "tax_exempt";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // 画面の表示順をそのまま保持する（明細の並び順に意味があるため）
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "description", nullable = false, length = 100)
    private String description;

    // 0.5人日のような小数の数量を扱えるようDECIMAL(10,2)とする
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @Column(name = "tax_category", nullable = false, length = 20)
    private String taxCategory = TAX_CATEGORY_TAXABLE_10;

    @Column(name = "tax_rate", nullable = false)
    private Integer taxRate = 10;

    // 数量×税抜単価（1円未満切り捨て）。Service側で計算してセットする
    @Column(name = "amount", nullable = false)
    private Integer amount;
}
