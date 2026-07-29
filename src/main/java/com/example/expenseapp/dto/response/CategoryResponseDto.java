package com.example.expenseapp.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryResponseDto {

	private Integer id;
    private String name;
    private Integer displayOrder;

}
