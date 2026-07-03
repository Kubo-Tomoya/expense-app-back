package com.example.expenseapp.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "expenses")
@SQLDelete(sql = "UPDATE expenses SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Expense {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "memo")
    private String memo;

    @Column(name = "receipt_image_path", length = 255)
    private String receiptImagePath;

    @Column(name = "status", nullable = false)
    private String status = "registered";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    
}
