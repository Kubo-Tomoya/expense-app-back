package com.example.expenseapp.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.expenseapp.dto.request.CategoryOrderRequestDto;
import com.example.expenseapp.dto.request.CategoryRequestDto;
import com.example.expenseapp.dto.response.CategoryResponseDto;
import com.example.expenseapp.security.UserPrincipal;
import com.example.expenseapp.service.CategoryService;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // GET /api/categories
    // カテゴリ一覧取得。is_activeを問わず全件返し、絞り込みはフロント側で行う
    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(categoryService.findAll(principal.getUser()));
    }

    // POST /api/categories
    // 新規登録（F-28）。display_orderは既存の最大値+1で自動採番する
    @PostMapping
    public ResponseEntity<CategoryResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CategoryRequestDto dto) {
        CategoryResponseDto category = categoryService.create(principal.getUser(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    // PUT /api/categories/order
    // 並び替え（F-28）。{id}を取るパスより先に定義しているのは、
    // "order"がIDとして解釈されないようにするため（F-20の/summaryと同じ理由）
    @PutMapping("/order")
    public ResponseEntity<List<CategoryResponseDto>> updateOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CategoryOrderRequestDto dto) {
        return ResponseEntity.ok(categoryService.updateOrder(principal.getUser(), dto));
    }

    // PUT /api/categories/{id}
    // 更新（F-28）。カテゴリ名と勘定科目のみ。表示順は並び替えAPIで変更する
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody CategoryRequestDto dto) {
        return ResponseEntity.ok(categoryService.update(principal.getUser(), id, dto));
    }

    // PUT /api/categories/{id}/deactivate
    // 無効化（F-28）。使用中でも無効化できる。過去の経費の表示・集計は変わらない
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<CategoryResponseDto> deactivate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(categoryService.deactivate(principal.getUser(), id));
    }

    // PUT /api/categories/{id}/activate
    // 再有効化（F-28）。誤って無効化した場合の復帰手段。冪等に扱う
    @PutMapping("/{id}/activate")
    public ResponseEntity<CategoryResponseDto> activate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(categoryService.activate(principal.getUser(), id));
    }

}
