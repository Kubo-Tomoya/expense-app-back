package com.example.expenseapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.expenseapp.dto.response.CategoryResponseDto;
import com.example.expenseapp.service.CategoryService;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "http://localhost:5173")
public class CategoryController {

	private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // GET /api/categories
    // カテゴリ一覧取得
    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getAll() {
        List<CategoryResponseDto> categories = categoryService.findAll();
        return ResponseEntity.ok(categories);
    }

}
