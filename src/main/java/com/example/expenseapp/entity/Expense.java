package com.example.expenseapp.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "expenses")
@SQLDelete(sql = "UPDATE expenses SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Expense {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
	
	// 追加するフィールド（category と同様の書き方）
	// この経費を登録したユーザー。他ユーザーのデータと混在しないよう必須項目とする
	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "memo")
    private String memo;

    @Column(name = "receipt_image_path", length = 255)
    private String receiptImagePath;

    @Column(name = "status", nullable = false)
    private String status = "registered";

    // --- F-21 消費税区分 ---
    // 金額（amount）は税込で保持し、税抜金額・消費税額は列として持たない。
    // 必要な箇所ではTaxCalculatorで税込金額と区分から算出する
    // （F-20で集計金額を税込に統一したため、同じ金額を2系統で管理しない）
    public static final String TAX_CATEGORY_TAXABLE_10 = "taxable_10";
    public static final String TAX_CATEGORY_TAXABLE_8 = "taxable_8";
    public static final String TAX_CATEGORY_TAX_EXEMPT = "tax_exempt";
    public static final String TAX_CATEGORY_NON_TAXABLE = "non_taxable";

    @Column(name = "tax_category", nullable = false, length = 20)
    private String taxCategory = TAX_CATEGORY_TAXABLE_10;

    // --- F-22 適格請求書チェック ---
    // 受領した領収書が適格請求書かどうか。課税区分以外はnull。
    // 「対象外（非課税・不課税）」と「課税だが未判定」を区別できるようにするため、
    // falseを既定値とせずnull許容にしている
    @Column(name = "is_qualified_invoice")
    private Boolean isQualifiedInvoice;

    // 支払先のインボイス登録番号（T＋数字13桁で14文字）。
    // F-15のbusiness_profiles.invoice_registration_numberと同じ長さに揃える
    @Column(name = "vendor_registration_number", length = 14)
    private String vendorRegistrationNumber;

    /**
     * 消費税の課税区分（10%または軽減8%）かどうか。
     * 適格請求書の情報を保持してよいかの判定に使う
     */
    public boolean isTaxable() {
        return TAX_CATEGORY_TAXABLE_10.equals(taxCategory) || TAX_CATEGORY_TAXABLE_8.equals(taxCategory);
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    
}
