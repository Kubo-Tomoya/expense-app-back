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

    /** 消費税区分（F-21）。taxable_10 / taxable_8 / tax_exempt / non_taxable */
    private String taxCategory;

    /** 受領した領収書が適格請求書か（F-22）。課税区分以外はnull */
    private Boolean isQualifiedInvoice;

    /** 支払先のインボイス登録番号（F-22）。未入力はnull */
    private String vendorRegistrationNumber;

}
