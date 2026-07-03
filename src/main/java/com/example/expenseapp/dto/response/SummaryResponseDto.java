package com.example.expenseapp.dto.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SummaryResponseDto {

	private Integer year;
    private Integer month;
    private Integer totalAmount;
    private Map<String, Integer> categoryBreakdown;
}
