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