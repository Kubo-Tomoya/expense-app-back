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
 * ExpenseRequestDtoのBean Validation（@Pattern等）のみを対象にしたテスト。
 *
 * ExpenseServiceを一切経由せず、Validatorを直接使ってDTO自体の
 * アノテーション検証だけを確認する。Serviceのモックテストでは
 * バリデーション自体は検証できない（Serviceは検証済みの値を受け取る前提のため）
 */
class ExpenseRequestDtoValidationTest {

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

    private ExpenseRequestDto validDto() {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("テスト経費");
        dto.setAmount(1000);
        dto.setCategoryId(1);
        dto.setExpenseDate(LocalDate.of(2026, 7, 1));
        return dto;
    }

    @Test
    void statusにregisteredを指定した場合バリデーションエラーにならない() {
        ExpenseRequestDto dto = validDto();
        dto.setStatus("registered");

        Set<ConstraintViolation<ExpenseRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void statusにdraftを指定した場合バリデーションエラーにならない() {
        ExpenseRequestDto dto = validDto();
        dto.setStatus("draft");

        Set<ConstraintViolation<ExpenseRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void statusに不正な値を指定するとバリデーションエラーになるリグレッション() {
        ExpenseRequestDto dto = validDto();
        dto.setStatus("invalid_value");

        Set<ConstraintViolation<ExpenseRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("ステータスはregisteredまたはdraftのいずれかを指定してください");
    }

    // --- F-21 消費税区分 ------------------------------------------------------

    @Test
    void 消費税区分に4種類の値を指定した場合バリデーションエラーにならない() {
        for (String taxCategory : new String[] { "taxable_10", "taxable_8", "tax_exempt", "non_taxable" }) {
            ExpenseRequestDto dto = validDto();
            dto.setTaxCategory(taxCategory);

            assertThat(validator.validate(dto))
                .as("区分: %s", taxCategory)
                .isEmpty();
        }
    }

    // F-21 No.3
    @Test
    void 消費税区分に定義外の値を指定するとバリデーションエラーになる() {
        ExpenseRequestDto dto = validDto();
        dto.setTaxCategory("taxable_5");

        Set<ConstraintViolation<ExpenseRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("消費税区分はtaxable_10・taxable_8・tax_exempt・non_taxableのいずれかを指定してください");
    }

    // --- F-22 適格請求書チェック ---------------------------------------------

    // F-22 No.6
    @Test
    void 登録番号にTが無い場合バリデーションエラーになる() {
        ExpenseRequestDto dto = validDto();
        dto.setVendorRegistrationNumber("1234567890123");

        Set<ConstraintViolation<ExpenseRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("インボイス登録番号は「T」＋数字13桁で入力してください");
    }

    // F-22 No.7
    @Test
    void 登録番号の桁数が不足する場合バリデーションエラーになる() {
        ExpenseRequestDto dto = validDto();
        dto.setVendorRegistrationNumber("T123");

        assertThat(validator.validate(dto)).isNotEmpty();
    }

    // F-22 No.8
    @Test
    void 登録番号は任意項目である() {
        ExpenseRequestDto dto = validDto();
        // 未指定（null）と、フォームで一度入力して消した場合の空文字のどちらも通す
        assertThat(validator.validate(dto)).isEmpty();

        dto.setVendorRegistrationNumber("");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void 登録番号が正しい形式の場合バリデーションエラーにならない() {
        ExpenseRequestDto dto = validDto();
        dto.setVendorRegistrationNumber("T1234567890123");

        assertThat(validator.validate(dto)).isEmpty();
    }
}