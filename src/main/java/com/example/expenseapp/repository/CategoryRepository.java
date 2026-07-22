package com.example.expenseapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.expenseapp.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // ログイン中ユーザーが所有するカテゴリのみを取得する
    List<Category> findAllByUserIdOrderById(Integer userId);

    // カテゴリ1件取得時、「自分が所有するカテゴリか」を同時に確認するためのメソッド。
    // 単なるfindById(id)だと、他人のカテゴリIDを直接指定されても取得できてしまうため、
    // 必ずuserIdもセットで絞り込む
    Optional<Category> findByIdAndUserId(Integer id, Integer userId);
}