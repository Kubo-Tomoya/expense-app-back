package com.example.expenseapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.expenseapp.entity.Expense;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Integer>{

	// 月別で経費一覧を取得（expense_dateの年月で絞り込み）
	// user_idによる絞り込みを追加。メソッド名も内容に合わせて変更
	@Query(value = "SELECT * FROM expenses e WHERE e.user_id = :userId AND TO_CHAR(e.expense_date, 'YYYY-MM') = :month ORDER BY e.expense_date DESC", nativeQuery = true)
	List<Expense> findByUserIdAndMonth(@Param("userId") Integer userId, @Param("month") String month);

	// 月指定なしの全件取得用（これまでexpenseRepository.findAll()を使っていた箇所の置き換え）
	List<Expense> findAllByUserIdOrderByExpenseDateDesc(Integer userId);

	// 経費1件取得・更新・削除・領収書アップロード時、
	// 「自分が登録した経費か」を同時に確認するためのメソッド
	Optional<Expense> findByIdAndUserId(Integer id, Integer userId);

	// 年別・月別の集計データを取得（S-04集計画面・年間集計用）
	// F-27対応：年間集計・集計画面は下書きを含めない設計方針のため、
	// registeredのみに絞り込むよう修正（statusを固定値でWHERE条件に追加）
	@Query("SELECT MONTH(e.expenseDate), e.category.name, SUM(e.amount) " +
	       "FROM Expense e WHERE e.user.id = :userId AND YEAR(e.expenseDate) = :year AND e.status = 'registered' " +
	       "GROUP BY MONTH(e.expenseDate), e.category.name ORDER BY MONTH(e.expenseDate)")
	List<Object[]> findSummaryByUserIdAndYear(@Param("userId") Integer userId, @Param("year") Integer year);

	// 年別・月別・ステータス別の集計データを取得（S-00ダッシュボード用）
	// F-27対応：新規追加。ExpenseService#getSummaryが、確定分(registered)と
	// 下書き分(draft)を別々に集計するために、statusを引数として受け取れるようにしたもの
	@Query("SELECT MONTH(e.expenseDate), e.category.name, SUM(e.amount) " +
	       "FROM Expense e WHERE e.user.id = :userId AND YEAR(e.expenseDate) = :year AND e.status = :status " +
	       "GROUP BY MONTH(e.expenseDate), e.category.name ORDER BY MONTH(e.expenseDate)")
	List<Object[]> findSummaryByUserIdAndYearAndStatus(
	    @Param("userId") Integer userId, @Param("year") Integer year, @Param("status") String status);

}