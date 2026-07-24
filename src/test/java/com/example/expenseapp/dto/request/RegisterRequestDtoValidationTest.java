package com.example.expenseapp.dto.request;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * RegisterRequestDtoのBean Validation（@Size等）のみを対象にしたテスト。
 * F-27のExpenseRequestDtoValidationTestと同じ構成・目的
 */
class RegisterRequestDtoValidationTest {

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

    private RegisterRequestDto dtoWithPassword(String password) {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setEmail("test@example.com");
        dto.setPassword(password);
        return dto;
    }

    @Test
    void パスワード8文字ちょうどはバリデーションエラーにならない() {
        RegisterRequestDto dto = dtoWithPassword("12345678"); // ちょうど8文字

        Set<ConstraintViolation<RegisterRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void パスワード7文字はバリデーションエラーになる() {
        RegisterRequestDto dto = dtoWithPassword("1234567"); // 7文字（境界値の一歩手前）

        Set<ConstraintViolation<RegisterRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("パスワードは8文字以上で入力してください");
    }
}