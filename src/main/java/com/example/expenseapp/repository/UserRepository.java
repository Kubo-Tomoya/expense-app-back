package com.example.expenseapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.expenseapp.entity.User;

/**
 * usersテーブルへのDBアクセスを担当するRepository。
 * JpaRepositoryを継承するだけで、基本的なCRUD（保存・取得・更新・削除）は自動で使えるようになる。
 */
public interface UserRepository extends JpaRepository<User, Integer> {

	// ログイン時に「このメールアドレスのユーザーは存在するか」を検索するために使用
    Optional<User> findByEmail(String email);

    // 新規登録時の重複チェック用。
    // findByEmailで代用も可能だが、存在確認だけなら全件取得しないこちらの方が軽量
    boolean existsByEmail(String email);
    
}
