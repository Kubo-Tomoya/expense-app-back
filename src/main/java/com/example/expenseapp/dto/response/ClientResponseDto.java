package com.example.expenseapp.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClientResponseDto {
    private Integer id;
    private String name;
    private String honorific;
    private String contactPerson;
    private String address;
    private String email;
    private String phone;
    private String memo;
    private Boolean isActive;
    private LocalDateTime createdAt;
}