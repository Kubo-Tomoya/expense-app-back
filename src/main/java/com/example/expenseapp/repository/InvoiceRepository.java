package com.example.expenseapp.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // --- F-20 収支ダッシュボード拡張の集計 ---
    //
    // いずれもstatus = 'issued'のみを対象とする。下書き（draft）は取引先に渡していないため、
    // 取消済み（canceled）は請求自体を無かったことにする操作のため、売上・未回収に計上しない。
    // 金額は税込（total_amount）で集計する（経費側も税込管理のため基準を揃える）

    /**
     * 指定月の売上（発生主義）。発行日（issue_date）で期間を絞る
     */
    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i "
        + "WHERE i.user.id = :userId AND i.status = 'issued' "
        + "AND YEAR(i.issueDate) = :year AND MONTH(i.issueDate) = :month")
    Integer sumSalesByUserIdAndYearMonth(
        @Param("userId") Integer userId, @Param("year") Integer year, @Param("month") Integer month);

    /**
     * 指定年の月別売上。データが無い月は行自体が返らないため、Service側で0埋めする
     */
    @Query("SELECT MONTH(i.issueDate), COALESCE(SUM(i.totalAmount), 0) FROM Invoice i "
        + "WHERE i.user.id = :userId AND i.status = 'issued' AND YEAR(i.issueDate) = :year "
        + "GROUP BY MONTH(i.issueDate) ORDER BY MONTH(i.issueDate)")
    List<Object[]> findMonthlySalesByUserIdAndYear(
        @Param("userId") Integer userId, @Param("year") Integer year);

    /**
     * 未回収金額（発行済みかつ未入金）。
     * 月で絞らないのは、未回収が「債権残高」であり月次のフローではないため。
     * 当月発行分だけを集計すると過去に発行した未回収が抜け、資金繰りの確認に使えない
     */
    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i "
        + "WHERE i.user.id = :userId AND i.status = 'issued' AND i.paymentStatus = 'unpaid'")
    Integer sumUnpaidByUserId(@Param("userId") Integer userId);

    /**
     * 未回収のうち支払期日を過ぎたもの（督促対象）
     */
    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i "
        + "WHERE i.user.id = :userId AND i.status = 'issued' AND i.paymentStatus = 'unpaid' "
        + "AND i.dueDate < :today")
    Integer sumOverdueByUserId(@Param("userId") Integer userId, @Param("today") LocalDate today);
}
