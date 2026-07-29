package com.example.expenseapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.expenseapp.entity.Client;

public interface ClientRepository extends JpaRepository<Client, Integer> {

    // ログイン中ユーザーが所有する取引先を、会社名の昇順で取得する
    List<Client> findAllByUserIdOrderByName(Integer userId);

    // 取引先1件取得・更新・無効化時、「自分が所有する取引先か」を同時に確認するためのメソッド。
    // F-14と同じ設計思想（他人の取引先IDを直接指定されても取得できないようにする）
    Optional<Client> findByIdAndUserId(Integer id, Integer userId);
}