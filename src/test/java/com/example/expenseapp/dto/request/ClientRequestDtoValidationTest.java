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

class ClientRequestDtoValidationTest {

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

    @Test
    void 会社名が空の場合バリデーションエラーになる() {
        ClientRequestDto dto = new ClientRequestDto();
        dto.setName(""); // 空文字

        Set<ConstraintViolation<ClientRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("会社名／屋号は必須です");
    }

    @Test
    void メールアドレスの形式が不正な場合バリデーションエラーになる() {
        ClientRequestDto dto = new ClientRequestDto();
        dto.setName("株式会社サンプル商事"); // 必須項目は満たしておく
        dto.setEmail("invalid-format"); // 不正な形式

        Set<ConstraintViolation<ClientRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("メールアドレスの形式が正しくありません");
    }
}