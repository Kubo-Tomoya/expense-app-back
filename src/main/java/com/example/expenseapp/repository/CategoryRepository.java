package com.example.expenseapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.expenseapp.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // ログイン中ユーザーが所有するカテゴリのみを取得する
    List<Category> findAllByUserIdOrderById(Integer userId);

    // カテゴリ1件取得時、「自分が所有するカテゴリか」を同時に確認するためのメソッド。
    // 単なるfindById(id)だと、他人のカテゴリIDを直接指定されても取得できてしまうため、
    // 必ずuserIdもセットで絞り込む
    Optional<Category> findByIdAndUserId(Integer id, Integer userId);

    // --- F-28 カテゴリ管理 ---

    // 一覧・プルダウンの表示順は display_order に従う（S-15で並び替えできるようにしたため）。
    // 旧来の findAllByUserIdOrderById は登録順に依存しており、並び替えを反映できない
    List<Category> findAllByUserIdOrderByDisplayOrderAsc(Integer userId);

    // 同一ユーザー内での重複確認。DBの複合一意制約（user_id + name）と同じ条件を
    // Service側でも事前に確認し、分かりやすいメッセージで返すために使う
    Optional<Category> findByUserIdAndName(Integer userId, String name);

    // 新規登録時の display_order の採番に使う（既存の最大値 + 1 とする）
    @Query("SELECT COALESCE(MAX(c.displayOrder), 0) FROM Category c WHERE c.user.id = :userId")
    Integer findMaxDisplayOrderByUserId(@Param("userId") Integer userId);

    /**
     * カテゴリごとの使用件数（そのカテゴリを使う経費の件数）をまとめて取得する。
     * S-15の一覧で無効化の影響を判断するために表示する。
     *
     * 件数が0のカテゴリは行自体が返らないため、Service側で0を補う。
     * 論理削除済みの経費（deleted_at IS NOT NULL）は数えない
     */
    @Query("SELECT e.category.id, COUNT(e) FROM Expense e "
        + "WHERE e.user.id = :userId GROUP BY e.category.id")
    List<Object[]> countExpensesByCategory(@Param("userId") Integer userId);
}
