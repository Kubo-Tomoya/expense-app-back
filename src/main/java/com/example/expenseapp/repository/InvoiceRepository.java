package com.example.expenseapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.expenseapp.entity.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

    // ログイン中ユーザーが所有する請求書を、発行日の降順で取得する。
    // 年・ステータス・キーワードでの絞り込みはフロント側で行う（S-13の設計）
    List<Invoice> findAllByUserIdOrderByIssueDateDesc(Integer userId);

    // 1件取得・更新・発行・取消・削除時、「自分が所有する請求書か」を同時に確認するためのメソッド。
    // F-14と同じ設計思想（他人の請求書IDを直接指定されても取得できないようにする）
    Optional<Invoice> findByIdAndUserId(Integer id, Integer userId);
}
