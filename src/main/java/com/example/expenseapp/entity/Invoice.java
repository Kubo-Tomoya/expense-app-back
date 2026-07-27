package com.example.expenseapp.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * invoicesテーブルに対応するEntity。F-17（請求書作成）で新規作成。
 *
 * F-14と同じ設計思想で、user_idによりユーザーごとにデータを分離する。
 *
 * client_*・issuer_*は発行時点のスナップショット。client_idの参照だけだと、
 * 取引先を改名・住所変更した際に過去の発行済み請求書の記載内容が遡って変わってしまい、
 * 適格請求書として「発行時点の記載事項」を保持できないため、発行時に値をコピーして保持する
 */
@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice {

    // ステータス。draft（下書き）→ issued（発行済み）→ canceled（取消）の3状態。
    // 発行済みは編集不可とし、訂正は「取消＋再発行」で行う
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ISSUED = "issued";
    public static final String STATUS_CANCELED = "canceled";

    // 入金状況。F-19（入金管理）で使用する
    public static final String PAYMENT_STATUS_UNPAID = "unpaid";
    public static final String PAYMENT_STATUS_PAID = "paid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // この請求書を所有するユーザー
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 請求先の取引先（F-16）。無効化された取引先も過去の請求書からは参照され続ける
    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    // 発行時に採番するため、下書きの間はnull
    @Column(name = "invoice_number", length = 20)
    private String invoiceNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    @Column(name = "subtotal_amount", nullable = false)
    private Integer subtotalAmount = 0;

    @Column(name = "tax_amount", nullable = false)
    private Integer taxAmount = 0;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount = 0;

    // --- 発行時スナップショット（取引先） ---

    @Column(name = "client_name", length = 100)
    private String clientName;

    @Column(name = "client_honorific", length = 10)
    private String clientHonorific;

    @Column(name = "client_address", length = 255)
    private String clientAddress;

    // --- 発行時スナップショット（発行者＝F-15 事業者プロフィール） ---

    @Column(name = "issuer_business_name", length = 100)
    private String issuerBusinessName;

    @Column(name = "issuer_owner_name", length = 100)
    private String issuerOwnerName;

    @Column(name = "issuer_address", length = 255)
    private String issuerAddress;

    @Column(name = "issuer_invoice_registration_number", length = 14)
    private String issuerInvoiceRegistrationNumber;

    // --- 入金管理（F-19で使用。F-17では列と初期値のみ用意する） ---

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = PAYMENT_STATUS_UNPAID;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 発行操作を行った日時。issue_date（ユーザーが指定する発行日）とは別に監査用に保持する
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "canceled_reason", length = 255)
    private String canceledReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 明細。更新時は全行差し替え方式のため、orphanRemoval = trueで
     * リストから外した明細がDBからも削除されるようにしている
     */
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    /**
     * 明細を全行差し替える。双方向関連の親子付けもここで行う
     */
    public void replaceItems(List<InvoiceItem> newItems) {
        this.items.clear();
        for (InvoiceItem item : newItems) {
            item.setInvoice(this);
            this.items.add(item);
        }
    }

    public boolean isDraft() {
        return STATUS_DRAFT.equals(this.status);
    }

    public boolean isIssued() {
        return STATUS_ISSUED.equals(this.status);
    }
}
