package com.example.expenseapp.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.expenseapp.dto.response.CategoryResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.repository.CategoryRepository;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // カテゴリ一覧取得
    // ログイン中ユーザーのカテゴリのみを返す
    public List<CategoryResponseDto> findAll(User user) {
        return categoryRepository.findAllByUserIdOrderById(user.getId()).stream()
            .map(this::toResponseDto)
            .collect(Collectors.toList());
    }

    // Entity → ResponseDto 変換
    private CategoryResponseDto toResponseDto(Category category) {
        CategoryResponseDto dto = new CategoryResponseDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDisplayOrder(category.getDisplayOrder());
        return dto;
    }
}
