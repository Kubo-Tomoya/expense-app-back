package com.example.expenseapp.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.expenseapp.entity.InvoiceNumberSequence;
import com.example.expenseapp.entity.InvoiceNumberSequence.InvoiceNumberSequenceId;

@Repository
public interface InvoiceNumberSequenceRepository
        extends JpaRepository<InvoiceNumberSequence, InvoiceNumberSequenceId> {

    /**
     * 採番対象の行を排他ロック（SELECT ... FOR UPDATE）した状態で取得する。
     *
     * 同じユーザーが同時に2件発行した場合でも、片方のトランザクションが
     * この行のロックを解放するまでもう片方は待たされるため、
     * last_numberの読み取り→インクリメント→保存の間に割り込まれず、
     * 同一番号が2件採番されることを防げる
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceNumberSequence s WHERE s.userId = :userId AND s.year = :year")
    Optional<InvoiceNumberSequence> findByUserIdAndYearForUpdate(
            @Param("userId") Integer userId, @Param("year") Integer year);
}
