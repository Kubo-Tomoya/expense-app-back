package com.example.expenseapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.expenseapp.entity.BusinessProfile;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Integer> {

    // ログイン中ユーザーのプロフィールを取得する。
    // まだ一度も設定していないユーザーは該当レコードが無いため、Optional.empty()が返る
    Optional<BusinessProfile> findByUserId(Integer userId);
}