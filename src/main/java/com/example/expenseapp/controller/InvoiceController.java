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
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.example.expenseapp.dto.request.InvoiceCancelRequestDto;
import com.example.expenseapp.dto.request.InvoicePaymentRequestDto;
import com.example.expenseapp.dto.request.InvoiceRequestDto;
import com.example.expenseapp.dto.response.InvoiceResponseDto;
import com.example.expenseapp.security.UserPrincipal;
import com.example.expenseapp.service.InvoicePdfService;
import com.example.expenseapp.service.InvoiceService;

/**
 * 請求書関連のHTTPリクエストを受け付けるController（F-17）。
 *
 * 発行・取消は内容の更新（PUT /api/invoices/{id}）とは別のエンドポイントに分離している。
 * 発行は「請求書番号の採番」「スナップショットの確定」という副作用を伴う操作であり、
 * 汎用の更新APIでstatusを書き換える方式にすると、
 * 採番の排他制御と内容更新が同一処理に混在してしまうため
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    // GET /api/invoices
    // 請求書一覧取得（明細は含まない）。ログイン中ユーザーの請求書のみを返す
    @GetMapping
    public ResponseEntity<List<InvoiceResponseDto>> getAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(invoiceService.findAll(principal.getUser()));
    }

    // GET /api/invoices/{id}
    // 請求書1件取得（明細・税率別内訳を含む）。他人の請求書IDを指定された場合は404
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponseDto> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(invoiceService.findById(principal.getUser(), id));
    }

    // POST /api/invoices
    // 請求書の新規作成。常に下書き（status=draft・invoice_numberはnull）で作成される
    @PostMapping
    public ResponseEntity<InvoiceResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InvoiceRequestDto dto) {
        InvoiceResponseDto invoice = invoiceService.create(principal.getUser(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    // PUT /api/invoices/{id}
    // 請求書の更新（下書きのみ）。明細は全行差し替え
    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponseDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody InvoiceRequestDto dto) {
        return ResponseEntity.ok(invoiceService.update(principal.getUser(), id, dto));
    }

    // PUT /api/invoices/{id}/issue
    // 発行。請求書番号の採番と、取引先・発行者情報のスナップショット確定を行う
    @PutMapping("/{id}/issue")
    public ResponseEntity<InvoiceResponseDto> issue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(invoiceService.issue(principal.getUser(), id));
    }

    // PUT /api/invoices/{id}/cancel
    // 取消（発行済みのみ）。請求書番号は欠番にせずレコードを残す
    @PutMapping("/{id}/cancel")
    public ResponseEntity<InvoiceResponseDto> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody InvoiceCancelRequestDto dto) {
        return ResponseEntity.ok(invoiceService.cancel(principal.getUser(), id, dto.getReason()));
    }

    // PUT /api/invoices/{id}/payment-status
    // 入金状況の更新（F-19）。発行済みのみ。入金済みにする場合は入金日が必須
    //
    // 状態が2値の切替でpaid_atの更新しか伴わないため、発行・取消のように
    // 専用エンドポイントへ分けず1本で受け取る
    @PutMapping("/{id}/payment-status")
    public ResponseEntity<InvoiceResponseDto> updatePaymentStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody InvoicePaymentRequestDto dto) {
        return ResponseEntity.ok(invoiceService.updatePaymentStatus(
            principal.getUser(), id, dto.getPaymentStatus(), dto.getPaidAt()));
    }

    // GET /api/invoices/{id}/pdf
    // 請求書PDFの出力（F-18）。発行済み・取消済みのみ。下書きは400
    //
    // inlineで返すのは、フロントがblobとして受け取り別タブでプレビュー表示する運用のため。
    // ファイル名は請求書番号のみとし、日本語を含めない（ブラウザ・OSによる文字化けを避ける）
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        InvoicePdfService.PdfDocument pdf = invoicePdfService.generate(principal.getUser(), id);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + pdf.getFileName() + "\"")
            .body(pdf.getContent());
    }

    // DELETE /api/invoices/{id}
    // 下書きの削除（物理削除）。発行済み・取消済みは400で拒否される
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        invoiceService.delete(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
