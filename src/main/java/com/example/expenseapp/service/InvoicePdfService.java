package com.example.expenseapp.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.example.expenseapp.dto.response.InvoiceTaxSummaryDto;
import com.example.expenseapp.entity.Invoice;
import com.example.expenseapp.entity.InvoiceItem;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.InvalidInvoiceStateException;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.InvoiceRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * 請求書PDFの生成（F-18）。
 *
 * 記載内容は全て発行時スナップショット（invoices.client_*／issuer_*）と invoice_items から取得する。
 * 事業者プロフィール（F-15）や取引先（F-16）を直接参照しないため、
 * 発行後にそれらを変更してもPDFの記載は変わらない。
 *
 * PDFはサーバーに保存せず、リクエストごとに都度生成する
 * （スナップショットにより内容が凍結されているため、いつ生成しても同じPDFが再現できる）
 */
@Service
@Transactional(readOnly = true)
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

    private static final String TEMPLATE_NAME = "invoice-pdf";

    // テンプレートのCSSで指定しているfont-familyと一致させる論理フォント名
    private static final String PDF_FONT_FAMILY = "IPAexGothic";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日");

    // 税率区分の表示名。帳票では「課税10%」「軽減8%」「非課税」と表記する
    private static final Map<String, String> TAX_CATEGORY_LABELS = new LinkedHashMap<>();
    static {
        TAX_CATEGORY_LABELS.put(InvoiceItem.TAX_CATEGORY_TAXABLE_10, "課税10%");
        TAX_CATEGORY_LABELS.put(InvoiceItem.TAX_CATEGORY_TAXABLE_8, "軽減8%");
        TAX_CATEGORY_LABELS.put(InvoiceItem.TAX_CATEGORY_TAX_EXEMPT, "非課税");
    }

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final TemplateEngine templateEngine;

    /**
     * 埋め込む日本語フォントのクラスパス上の位置。
     * PDFはフォントを埋め込まないと日本語が表示できないため必須。
     * IPAexフォント（IPAフォントライセンス）は再配布・埋め込みが認められている
     */
    private final String fontPath;

    public InvoicePdfService(
            InvoiceRepository invoiceRepository,
            InvoiceService invoiceService,
            TemplateEngine templateEngine,
            @Value("${app.pdf.font-path:fonts/ipaexg.ttf}") String fontPath) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.templateEngine = templateEngine;
        this.fontPath = fontPath;
    }

    /**
     * 請求書PDFを生成する。
     *
     * 下書き（draft）は請求書番号が未採番で適格請求書の要件を満たさないため出力できない。
     * 取消済み（canceled）は「取消済」の透かし付きで出力する
     * （取引先へ送付済みの請求書の控えを、取消後も確認できる必要があるため）
     */
    public PdfDocument generate(User user, Integer id) {
        Invoice invoice = invoiceRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("請求書が見つかりません。ID: " + id));

        if (invoice.isDraft()) {
            throw new InvalidInvoiceStateException(
                "下書きの請求書はPDF出力できません。発行してから出力してください");
        }

        String html = renderHtml(invoice);
        // ファイル名は請求書番号のみ。日本語を含めないのは、ブラウザ・OSによる文字化けを避けるため
        return new PdfDocument(invoice.getInvoiceNumber() + ".pdf", toPdf(html));
    }

    /**
     * 生成したPDFと、レスポンスに載せるファイル名をまとめて返すための入れ物
     */
    public static class PdfDocument {
        private final String fileName;
        private final byte[] content;

        public PdfDocument(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }
    }

    // --- 内部処理 -----------------------------------------------------------

    private String renderHtml(Invoice invoice) {
        // 税率別内訳はinvoice_itemsから再集計する。発行済みの明細は変更されないため、
        // 再計算しても発行時と同じ値になる（InvoiceServiceと同一ロジックを共用する）
        InvoiceService.Calculation calc = invoiceService.calculate(invoice.getItems());

        List<Map<String, Object>> items = new ArrayList<>();
        boolean hasReducedRate = false;
        for (InvoiceItem item : invoice.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("description", item.getDescription());
            row.put("quantityLabel", formatQuantity(item.getQuantity()));
            row.put("unitPrice", item.getUnitPrice());
            row.put("taxCategory", item.getTaxCategory());
            row.put("taxRateLabel", item.getTaxRate() + "%");
            row.put("amount", item.getAmount());
            items.add(row);

            if (InvoiceItem.TAX_CATEGORY_TAXABLE_8.equals(item.getTaxCategory())) {
                hasReducedRate = true;
            }
        }

        List<Map<String, Object>> taxSummaries = new ArrayList<>();
        for (InvoiceTaxSummaryDto summary : calc.getTaxSummaries()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", TAX_CATEGORY_LABELS.getOrDefault(summary.getTaxCategory(), summary.getTaxCategory()));
            row.put("subtotalAmount", summary.getSubtotalAmount());
            row.put("taxAmount", summary.getTaxAmount());
            taxSummaries.add(row);
        }

        String registrationNumber = invoice.getIssuerInvoiceRegistrationNumber();
        // 登録番号が未設定の場合は「適格請求書」と表示せず、登録番号欄も出さない。
        // 免税事業者は登録番号を持たないため、出力自体は許可する（画面側で注意を促す）
        boolean isQualified = registrationNumber != null && !registrationNumber.isBlank();

        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("items", items);
        context.setVariable("taxSummaries", taxSummaries);
        context.setVariable("issueDate", invoice.getIssueDate().format(DATE_FORMAT));
        context.setVariable("dueDate", invoice.getDueDate().format(DATE_FORMAT));
        context.setVariable("isQualified", isQualified);
        context.setVariable("hasReducedRate", hasReducedRate);
        context.setVariable("isCanceled", Invoice.STATUS_CANCELED.equals(invoice.getStatus()));

        return templateEngine.process(TEMPLATE_NAME, context);
    }

    /**
     * 数量は小数第2位まで保持しているが、帳票では末尾の0を落として表示する
     * （1.00 → 1、0.50 → 0.5）
     */
    private String formatQuantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private byte[] toPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // 外部リソース（画像・CSS）は参照しないためbaseUriは不要
            builder.withHtmlContent(html, null);

            byte[] font = loadFont();
            if (font != null) {
                builder.useFont(() -> new ByteArrayInputStream(font), PDF_FONT_FAMILY);
            }

            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("請求書PDFの生成に失敗しました", e);
        }
    }

    /**
     * 埋め込みフォントを読み込む。
     * 見つからない場合はPDF自体は生成するが日本語が表示されないため、警告を残す
     * （セットアップ漏れを起動時ではなく出力時に気付けるようにする）
     */
    private byte[] loadFont() {
        ClassPathResource resource = new ClassPathResource(fontPath);
        if (!resource.exists()) {
            log.warn("PDF用の日本語フォントが見つかりません（{}）。日本語が表示されないPDFになります。"
                + "IPAexゴシック(ipaexg.ttf)をsrc/main/resources/{}に配置してください", fontPath, fontPath);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("PDF用の日本語フォントの読み込みに失敗しました（{}）", fontPath, e);
            return null;
        }
    }
}
