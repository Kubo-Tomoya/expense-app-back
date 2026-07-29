package com.example.expenseapp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * business_profilesテーブルに対応するEntity。
 * 1ユーザーにつき1レコードのみ存在する（OneToOne + user_idのUNIQUE制約）。
 *
 * 将来のPhase B（請求書発行）で、発行者情報としてそのまま使い回す想定
 */
@Getter
@Setter
@Entity
@Table(name = "business_profiles")
public class BusinessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // 1ユーザー1プロフィールのため、多対一ではなく一対一の関連にする
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "business_name", length = 100)
    private String businessName; // 屋号（任意）

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName; // 氏名（必須）

    @Column(name = "address", length = 255)
    private String address; // 住所（任意）

    @Column(name = "invoice_registration_number", length = 14)
    private String invoiceRegistrationNumber; // インボイス登録番号（任意。T+13桁）

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}