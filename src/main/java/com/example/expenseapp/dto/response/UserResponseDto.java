package com.example.expenseapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ユーザー情報のレスポンス形式。
 * Userエンティティをそのまま返さず、id・emailだけを持つ専用DTOを用意することで、
 * passwordHashが誤ってレスポンスに含まれ外部に漏れる事故を構造的に防いでいる
 */
@Getter
@AllArgsConstructor
public class UserResponseDto {
	
	private Integer id;
    private String email;
    
}
