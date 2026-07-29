package com.example.expenseapp.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * invoice_number_sequencesテーブルに対応するEntity。F-17（請求書作成）で新規作成。
 *
 * ユーザー単位・年単位で請求書番号の連番を管理する。主キーは(user_id, year)の複合主キー。
 *
 * userをManyToOneにせずuserIdをそのまま持っているのは、
 * 採番時にこの行だけを排他ロックして更新する用途に限定されており、
 * User側の情報を辿る必要がないため（ロック対象を最小限にする狙いもある）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invoice_number_sequences")
@IdClass(InvoiceNumberSequence.InvoiceNumberSequenceId.class)
public class InvoiceNumberSequence {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    // 発行日（issue_date）の年。発行操作日ではなく会計年度に合わせる
    @Id
    @Column(name = "year", nullable = false)
    private Integer year;

    // 直近で発行済みの連番。取消しても戻さない（番号の再利用を避けるため）
    @Column(name = "last_number", nullable = false)
    private Integer lastNumber = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 複合主キー用のクラス。JPAの仕様上、equals/hashCodeとデフォルトコンストラクタが必要
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class InvoiceNumberSequenceId implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer userId;
        private Integer year;
    }
}
