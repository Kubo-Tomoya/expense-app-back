package com.example.expenseapp.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.example.expenseapp.dto.response.YearSummaryResponseDto;
import com.example.expenseapp.security.UserPrincipal;
import com.example.expenseapp.service.ExpenseService;

/**
 * 経費関連のHTTPリクエストを受け付けるController。
 * CORS設定は WebConfig.java に集約したため、ここでは付与しない。
 *
 * F-14対応：全メソッドで @AuthenticationPrincipal UserPrincipal を受け取り、
 * ログイン中ユーザーの情報をServiceに渡すことで、
 * 「自分のデータしか操作できない」を徹底している。
 * この引数はSpring Securityが、認証成功時にセッションへ保存した
 * UserPrincipal（Step1-9参照）を自動的に注入してくれる
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

	private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // GET /api/expenses
    // 経費一覧取得（月指定可）。ログイン中ユーザーの経費のみを返す
    @GetMapping
    public ResponseEntity<List<ExpenseResponseDto>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String month) {
        List<ExpenseResponseDto> expenses = expenseService.findAll(principal.getUser(), month);
        return ResponseEntity.ok(expenses);
    }

    // GET /api/expenses/{id}
    // 経費1件取得。他人の経費IDを指定された場合は404を返す（findByIdAndUserId経由）
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        ExpenseResponseDto expense = expenseService.findById(principal.getUser(), id);
        return ResponseEntity.ok(expense);
    }

    // POST /api/expenses
    // 経費新規登録。登録者としてログイン中ユーザーを紐付ける
    @PostMapping
    public ResponseEntity<ExpenseResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ExpenseRequestDto dto) {
        ExpenseResponseDto expense = expenseService.create(principal.getUser(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }

    // PUT /api/expenses/{id}
    // 経費更新。他人の経費・他人のカテゴリIDを指定された場合はService側で404を返す
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody ExpenseRequestDto dto) {
        ExpenseResponseDto expense = expenseService.update(principal.getUser(), id, dto);
        return ResponseEntity.ok(expense);
    }

    // DELETE /api/expenses/{id}
    // 経費削除（論理削除）。他人の経費IDを指定された場合は404を返す
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        expenseService.delete(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/expenses/summary
    // 月次集計取得（S-00ダッシュボード用）。ログイン中ユーザーの経費のみを集計対象にする
    @GetMapping("/summary")
    public ResponseEntity<SummaryResponseDto> getSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month) {
        SummaryResponseDto summary = expenseService.getSummary(principal.getUser(), year, month);
        return ResponseEntity.ok(summary);
    }

    // GET /api/expenses/summary/yearly
    // 年間集計取得（S-04集計画面用）。ログイン中ユーザーの経費のみを集計対象にする
    @GetMapping("/summary/yearly")
    public ResponseEntity<YearSummaryResponseDto> getYearlySummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Integer year) {
        YearSummaryResponseDto summary = expenseService.getYearlySummary(principal.getUser(), year);
        return ResponseEntity.ok(summary);
    }

    // POST /api/expenses/{id}/receipt
    // 領収書アップロード。他人の経費IDを指定された場合は404を返す
    @PostMapping("/{id}/receipt")
    public ResponseEntity<ExpenseResponseDto> uploadReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file) {
        ExpenseResponseDto expense = expenseService.uploadReceipt(principal.getUser(), id, file);
        return ResponseEntity.ok(expense);
    }

}