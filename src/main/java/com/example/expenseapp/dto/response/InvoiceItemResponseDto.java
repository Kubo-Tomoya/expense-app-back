package com.example.expenseapp.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoiceItemResponseDto {
    private Integer id;
    private Integer displayOrder;
    private String description;
    private BigDecimal quantity;
    private Integer unitPrice;   // 税抜単価
    private String taxCategory;  // taxable_10 / taxable_8 / tax_exempt
    private Integer taxRate;     // 発行時点の税率（％）
    private Integer amount;      // 数量×税抜単価（1円未満切り捨て）
}
