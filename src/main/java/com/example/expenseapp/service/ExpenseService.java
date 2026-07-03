package com.example.expenseapp.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.expenseapp.dto.request.ExpenseRequestDto;
import com.example.expenseapp.dto.response.ExpenseResponseDto;
import com.example.expenseapp.dto.response.SummaryResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.Expense;
import com.example.expenseapp.repository.CategoryRepository;
import com.example.expenseapp.repository.ExpenseRepository;

@Service
@Transactional
public class ExpenseService {

	private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            CategoryRepository categoryRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
    }

    // 経費一覧取得（月指定）
    public List<ExpenseResponseDto> findAll(String month) {
        List<Expense> expenses;
        if (month != null && !month.isEmpty()) {
            expenses = expenseRepository.findByMonth(month);
        } else {
            expenses = expenseRepository.findAll();
        }
        return expenses.stream()
            .map(this::toResponseDto)
            .collect(Collectors.toList());
    }

    // 経費1件取得
    public ExpenseResponseDto findById(Integer id) {
        Expense expense = expenseRepository.findById(id)
            .orElseThrow(() ->
                new RuntimeException("経費が見つかりません。ID: " + id));
        return toResponseDto(expense);
    }

    // 経費新規登録
    public ExpenseResponseDto create(ExpenseRequestDto dto) {
        Expense expense = toEntity(dto);
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        Expense saved = expenseRepository.save(expense);
        return toResponseDto(saved);
    }

    // 経費更新
    public ExpenseResponseDto update(Integer id, ExpenseRequestDto dto) {
        Expense expense = expenseRepository.findById(id)
            .orElseThrow(() ->
                new RuntimeException("経費が見つかりません。ID: " + id));
        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() ->
                new RuntimeException("カテゴリが見つかりません。ID: "
                    + dto.getCategoryId()));
        expense.setTitle(dto.getTitle());
        expense.setAmount(dto.getAmount());
        expense.setCategory(category);
        expense.setExpenseDate(dto.getExpenseDate());
        expense.setMemo(dto.getMemo());
        expense.setStatus(dto.getStatus());
        Expense saved = expenseRepository.save(expense);
        return toResponseDto(saved);
    }

    // 経費削除（論理削除）
    public void delete(Integer id) {
        Expense expense = expenseRepository.findById(id)
            .orElseThrow(() ->
                new RuntimeException("経費が見つかりません。ID: " + id));
        expenseRepository.delete(expense);
    }

    // 月次集計取得
    public SummaryResponseDto getSummary(Integer year, Integer month) {
        List<Object[]> results =
            expenseRepository.findSummaryByYear(year);
        Map<String, Integer> categoryBreakdown = new HashMap<>();
        int totalAmount = 0;
        for (Object[] row : results) {
            Integer rowMonth = ((Number) row[0]).intValue();
            String categoryName = (String) row[1];
            Integer amount = ((Number) row[2]).intValue();
            if (month == null || rowMonth.equals(month)) {
                categoryBreakdown.merge(categoryName, amount, Integer::sum);
                totalAmount += amount;
            }
        }
        SummaryResponseDto dto = new SummaryResponseDto();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setTotalAmount(totalAmount);
        dto.setCategoryBreakdown(categoryBreakdown);
        return dto;
    }

    // Entity → ResponseDto 変換
    private ExpenseResponseDto toResponseDto(Expense expense) {
        ExpenseResponseDto dto = new ExpenseResponseDto();
        dto.setId(expense.getId());
        dto.setTitle(expense.getTitle());
        dto.setAmount(expense.getAmount());
        dto.setCategoryName(expense.getCategory().getName());
        dto.setExpenseDate(expense.getExpenseDate());
        dto.setMemo(expense.getMemo());
        dto.setReceiptImagePath(expense.getReceiptImagePath());
        dto.setStatus(expense.getStatus());
        dto.setCreatedAt(expense.getCreatedAt());
        return dto;
    }

    // RequestDto → Entity 変換
    private Expense toEntity(ExpenseRequestDto dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() ->
                new RuntimeException("カテゴリが見つかりません。ID: "
                    + dto.getCategoryId()));
        Expense expense = new Expense();
        expense.setTitle(dto.getTitle());
        expense.setAmount(dto.getAmount());
        expense.setCategory(category);
        expense.setExpenseDate(dto.getExpenseDate());
        expense.setMemo(dto.getMemo());
        expense.setStatus(dto.getStatus());
        return expense;
    }

}
