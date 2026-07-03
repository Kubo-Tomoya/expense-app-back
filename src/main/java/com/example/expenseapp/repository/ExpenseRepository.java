package com.example.expenseapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.expenseapp.entity.Expense;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Integer>{
	
	// 月別で経費一覧を取得（expense_dateの年月で絞り込み）
	@Query(value = "SELECT * FROM expenses e WHERE e.deleted_at IS NULL " +
	        "AND TO_CHAR(e.expense_date, 'YYYY-MM') = :month " +
	        "ORDER BY e.expense_date DESC", nativeQuery = true)
	List<Expense> findByMonth(@Param("month") String month);

    // 年別・月別の集計データを取得
    @Query("SELECT MONTH(e.expenseDate), " +
           "e.category.name, " +
           "SUM(e.amount) " +
           "FROM Expense e " +
           "WHERE YEAR(e.expenseDate) = :year " +
           "GROUP BY MONTH(e.expenseDate), e.category.name " +
           "ORDER BY MONTH(e.expenseDate)")
    List<Object[]> findSummaryByYear(@Param("year") Integer year);

}
