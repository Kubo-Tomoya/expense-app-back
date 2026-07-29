package com.example.expenseapp.dto.request;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 入金状況リクエストのバリデーションテスト（F-19）。
 *
 * 「入金済みなら入金日が必須」「未来日は不可」は単一項目では表現できないため、
 * DTO側の相関チェック（@AssertTrue）として実装している
 */
class InvoicePaymentRequestDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        factory.close();
    }

    private InvoicePaymentRequestDto dto(String status, LocalDate paidAt) {
        InvoicePaymentRequestDto dto = new InvoicePaymentRequestDto();
        dto.setPaymentStatus(status);
        dto.setPaidAt(paidAt);
        return dto;
    }

    // No.8
    @Test
    void 不正な入金状況は拒否される() {
        Set<ConstraintViolation<InvoicePaymentRequestDto>> violations =
            validator.validate(dto("invalid", LocalDate.now()));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("入金状況は未入金／入金済みのいずれかを指定してください");
    }

    // No.9
    @Test
    void 入金済みにする場合は入金日が必須() {
        Set<ConstraintViolation<InvoicePaymentRequestDto>> violations =
            validator.validate(dto("paid", null));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("入金済みにする場合は入金日を入力してください");
    }

    // No.10
    @Test
    void 入金日に未来日は指定できない() {
        Set<ConstraintViolation<InvoicePaymentRequestDto>> violations =
            validator.validate(dto("paid", LocalDate.now().plusDays(1)));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("入金日に未来の日付は指定できません");
    }

    @Test
    void 当日の入金日は許容される() {
        assertThat(validator.validate(dto("paid", LocalDate.now()))).isEmpty();
    }

    @Test
    void 解除の場合は入金日が無くてもエラーにならない() {
        assertThat(validator.validate(dto("unpaid", null))).isEmpty();
    }
}
