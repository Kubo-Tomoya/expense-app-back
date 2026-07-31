package com.example.expenseapp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

// nameの一意制約を列単体からuser_id+nameの複合制約に変更
// （DBのマイグレーションSQLと整合させる必要がある。ddl-auto: validate のため、
//   ここがズレていると起動時にスキーマ検証エラーになる点に注意）
@Getter
@Setter
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class Category {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    // --- F-28 カテゴリ管理 ---
    // 確定申告の勘定科目名（任意）。F-23の勘定科目別集計でグルーピングのキーに使い、
    // 未設定の場合は「雑費」として集計する。F-25のfreee連携でもマッピングキーとして兼用する。
    // 7/11版のF-14で定義していたがPhase Aの実装から漏れていた列
    @Column(name = "tax_form_category", length = 50)
    private String taxFormCategory;

    // 有効フラグ。取引先（clients）と同じ無効化方式を採る。
    // 経費（F-04）はdeleted_atの論理削除だが、カテゴリは過去の経費から参照され続けるため、
    // レコードを消すのではなく「今後の選択肢から外す」フラグが実態に合う
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // このカテゴリを所有するユーザー。ユーザーごとに独自のカテゴリ一覧を持てるようにする
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // F-14テスト実装時に発覚：このフィールド自体が存在せず、
    // db/11_add_updated_at_to_categories.sqlでのカラム追加とあわせて新規追加
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}