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
}