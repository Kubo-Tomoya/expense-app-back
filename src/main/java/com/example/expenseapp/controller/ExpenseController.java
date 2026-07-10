package com.example.expenseapp.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.expenseapp.dto.request.ExpenseRequestDto;
import com.example.expenseapp.dto.response.ExpenseResponseDto;
import com.example.expenseapp.dto.response.SummaryResponseDto;
import com.example.expenseapp.service.ExpenseService;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "http://localhost:5173")
public class ExpenseController {

	private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // GET /api/expenses
    // 経費一覧取得（月指定可）
    @GetMapping
    public ResponseEntity<List<ExpenseResponseDto>> getAll(
            @RequestParam(required = false) String month) {
        List<ExpenseResponseDto> expenses = expenseService.findAll(month);
        return ResponseEntity.ok(expenses);
    }

    // GET /api/expenses/{id}
    // 経費1件取得
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> getById(
            @PathVariable Integer id) {
        ExpenseResponseDto expense = expenseService.findById(id);
        return ResponseEntity.ok(expense);
    }

    // POST /api/expenses
    // 経費新規登録
    @PostMapping
    public ResponseEntity<ExpenseResponseDto> create(
            @Valid @RequestBody ExpenseRequestDto dto) {
        ExpenseResponseDto expense = expenseService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }

    // PUT /api/expenses/{id}
    // 経費更新
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> update(
            @PathVariable Integer id,
            @Valid @RequestBody ExpenseRequestDto dto) {
        ExpenseResponseDto expense = expenseService.update(id, dto);
        return ResponseEntity.ok(expense);
    }

    // DELETE /api/expenses/{id}
    // 経費削除（論理削除）
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/expenses/summary
    // 月次集計取得
    @GetMapping("/summary")
    public ResponseEntity<SummaryResponseDto> getSummary(
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month) {
        SummaryResponseDto summary = expenseService.getSummary(year, month);
        return ResponseEntity.ok(summary);
    }
    
    //領収書アップロード機能の追加
    @PostMapping("/{id}/receipt")
    public ResponseEntity<ExpenseResponseDto> uploadReceipt(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file) {
        ExpenseResponseDto expense = expenseService.uploadReceipt(id, file);
        return ResponseEntity.ok(expense);
    }

}
