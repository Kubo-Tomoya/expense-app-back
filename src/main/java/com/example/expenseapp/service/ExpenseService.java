package com.example.expenseapp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.expenseapp.dto.request.ExpenseRequestDto;
import com.example.expenseapp.dto.response.ExpenseResponseDto;
import com.example.expenseapp.dto.response.MonthlySummaryResponseDto;
import com.example.expenseapp.dto.response.SummaryResponseDto;
import com.example.expenseapp.dto.response.YearSummaryResponseDto;
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

    // 月次集計取得（S-00ダッシュボード用：指定した1年・1月の合計を返す）
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

    /**
     * 年間集計取得（S-04集計画面用）。
     * 指定した1年分の経費を「月×カテゴリ」の内訳付きで集計する。
     *
     * 既存の findSummaryByYear（月・カテゴリごとにSUMしたSQL結果）を再利用し、
     * バラバラな行データを「1〜12月、各月ごとのカテゴリ内訳」という
     * 画面が扱いやすい形に組み立て直しているだけで、新しいSQLは発行していない。
     *
     * データが存在しない月も0円の月として結果に含める
     * （そうしないと集計テーブルの行が歯抜けになるため）。
     */
    public YearSummaryResponseDto getYearlySummary(Integer year) {
        List<Object[]> results = expenseRepository.findSummaryByYear(year);

        // 月ごとに「カテゴリ名→金額」のマップを溜めていく（TreeMapで1月→12月の順を保証）
        Map<Integer, Map<String, Integer>> byMonth = new TreeMap<>();
        // カテゴリ別の年間合計（画面の「カテゴリ別モード」用）
        Map<String, Integer> categoryTotals = new HashMap<>();
        int totalAmount = 0;

        for (Object[] row : results) {
            Integer m = ((Number) row[0]).intValue();
            String categoryName = (String) row[1];
            Integer amount = ((Number) row[2]).intValue();
            byMonth.computeIfAbsent(m, k -> new HashMap<>()).merge(categoryName, amount, Integer::sum);
            categoryTotals.merge(categoryName, amount, Integer::sum);
            totalAmount += amount;
        }

        // 1〜12月を必ず埋めて、データが無い月も0円として結果に含める
        List<MonthlySummaryResponseDto> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Map<String, Integer> breakdown = byMonth.getOrDefault(m, Collections.emptyMap());
            int monthTotal = breakdown.values().stream().mapToInt(Integer::intValue).sum();
            monthly.add(new MonthlySummaryResponseDto(m, monthTotal, breakdown));
        }

        YearSummaryResponseDto dto = new YearSummaryResponseDto();
        dto.setYear(year);
        dto.setTotalAmount(totalAmount);
        dto.setCategoryTotals(categoryTotals);
        dto.setMonthly(monthly);
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

    // 領収書アップロード
    public ExpenseResponseDto uploadReceipt(Integer id, MultipartFile file) {
        Expense expense = expenseRepository.findById(id)
            .orElseThrow(() ->
                new RuntimeException("経費が見つかりません。ID: " + id));

        if (file.isEmpty()) {
            throw new RuntimeException("ファイルが空です");
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new RuntimeException("JPEGまたはPNG形式のみアップロード可能です");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("ファイルサイズは5MB以内にしてください");
        }

        try {
            Path uploadPath = Paths.get("uploads");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
            String fileName = "receipt_" + id + "_" + System.currentTimeMillis() + extension;
            Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

            expense.setReceiptImagePath("/uploads/" + fileName);
            expense.setUpdatedAt(LocalDateTime.now());
            Expense saved = expenseRepository.save(expense);
            return toResponseDto(saved);
        } catch (IOException e) {
            throw new RuntimeException("ファイル保存に失敗しました", e);
        }
    }

}