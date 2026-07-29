package com.example.expenseapp.dto.request;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 請求書リクエストのバリデーションテスト（F-17）。
 * ClientRequestDtoValidationTestと同じ形式で、Validatorを直接使って検証する
 */
class InvoiceRequestDtoValidationTest {

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

    private InvoiceItemRequestDto validItem() {
        InvoiceItemRequestDto item = new InvoiceItemRequestDto();
        item.setDescription("Webサイト制作");
        item.setQuantity(new BigDecimal("1.00"));
        item.setUnitPrice(100000);
        item.setTaxCategory("taxable_10");
        return item;
    }

    private InvoiceRequestDto validRequest() {
        InvoiceRequestDto dto = new InvoiceRequestDto();
        dto.setClientId(1);
        dto.setIssueDate(LocalDate.of(2026, 7, 27));
        dto.setItems(List.of(validItem()));
        return dto;
    }

    // No.24
    @Test
    void 明細が0件のリクエストは拒否される() {
        InvoiceRequestDto dto = validRequest();
        dto.setItems(List.of());

        Set<ConstraintViolation<InvoiceRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("明細は1行以上入力してください");
    }

    // No.25
    @Test
    void 数量が0以下の明細は拒否される() {
        InvoiceItemRequestDto item = validItem();
        item.setQuantity(BigDecimal.ZERO);

        InvoiceRequestDto dto = validRequest();
        dto.setItems(List.of(item));

        Set<ConstraintViolation<InvoiceRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("数量は0より大きい値を入力してください");
    }

    // No.26
    @Test
    void 不正な税率区分は拒否される() {
        InvoiceItemRequestDto item = validItem();
        item.setTaxCategory("invalid_value");

        InvoiceRequestDto dto = validRequest();
        dto.setItems(List.of(item));

        Set<ConstraintViolation<InvoiceRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("税率区分は課税10%／軽減8%／非課税のいずれかを指定してください");
    }

    @Test
    void 必須項目が揃っていればエラーにならない() {
        Set<ConstraintViolation<InvoiceRequestDto>> violations = validator.validate(validRequest());

        assertThat(violations).isEmpty();
    }
}
