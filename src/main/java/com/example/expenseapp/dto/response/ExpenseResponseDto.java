package com.example.expenseapp.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseResponseDto {

	private Integer id;
    private String title;
    private Integer amount;
    private String categoryName;
    private LocalDate expenseDate;
    private String memo;
    private String receiptImagePath;
    private String status;
    private LocalDateTime createdAt;

}
