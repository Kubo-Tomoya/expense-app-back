package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.expenseapp.TestcontainersConfig;
import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.Invoice;
import com.example.expenseapp.entity.InvoiceItem;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.InvalidInvoiceStateException;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.InvoiceRepository;

/**
 * InvoicePdfServiceの単体テスト（F-18）。
 *
 * PDFはバイナリのため、生成結果をPDFBoxで読み戻してテキスト抽出し、
 * 記載項目の有無で検証する。レイアウト（座標・余白）の検証は自動化に見合わないため目視確認で代替する。
 *
 * ThymeleafのTemplateEngineを実物のまま使う必要があるため、
 * MockitoのみのテストではなくSpringのコンテキストを起動している。
 * InvoiceRepositoryのみ@MockitoBeanで差し替え、DBアクセスを伴わない形にしている
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class InvoicePdfServiceTest {

    @Autowired
    private InvoicePdfService invoicePdfService;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private MailService mailService;

    private User userA;
    private Client client;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1);
        userA.setEmail("userA@example.com");

        client = new Client();
        client.setId(10);
        client.setUser(userA);
        client.setName("株式会社サンプル商事");
        client.setHonorific("御中");
        client.setIsActive(true);
    }

    private InvoiceItem item(String description, String quantity, int unitPrice, String taxCategory, int taxRate) {
        InvoiceItem it = new InvoiceItem();
        it.setDisplayOrder(0);
        it.setDescription(description);
        it.setQuantity(new BigDecimal(quantity));
        it.setUnitPrice(unitPrice);
        it.setTaxCategory(taxCategory);
        it.setTaxRate(taxRate);
        it.setAmount(new BigDecimal(quantity).multiply(BigDecimal.valueOf(unitPrice)).intValue());
        return it;
    }

    /** 発行済みの請求書（スナップショット済み）を組み立てる */
    private Invoice issuedInvoice(List<InvoiceItem> items) {
        Invoice invoice = new Invoice();
        invoice.setId(100);
        invoice.setUser(userA);
        invoice.setClient(client);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(Invoice.STATUS_ISSUED);
        invoice.setIssueDate(LocalDate.of(2026, 7, 28));
        invoice.setDueDate(LocalDate.of(2026, 8, 31));
        invoice.replaceItems(items);

        int subtotal = items.stream().mapToInt(InvoiceItem::getAmount).sum();
        invoice.setSubtotalAmount(subtotal);
        invoice.setTaxAmount(0);
        invoice.setTotalAmount(subtotal);

        // 発行時スナップショット
        invoice.setClientName("株式会社サンプル商事");
        invoice.setClientHonorific("御中");
        invoice.setClientAddress("東京都千代田区1-1-1");
        invoice.setIssuerBusinessName("サンプル工房");
        invoice.setIssuerOwnerName("山田太郎");
        invoice.setIssuerAddress("東京都新宿区2-2-2");
        invoice.setIssuerInvoiceRegistrationNumber("T1234567890123");
        return invoice;
    }

    private Invoice defaultIssuedInvoice() {
        return issuedInvoice(List.of(
            item("Webサイト制作", "1", 100000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10)));
    }

    /**
     * PDFからテキストを抽出し、比較しやすい形に正規化する。
     *
     * 正規化が必要な理由：
     * ・PDFBoxの抽出では、フォントのcmapで康熙部首（⼭・⽥・⾦等）と同じグリフを共有する漢字が
     * 　部首側のコードポイントとして取り出される。NFKC正規化で通常の漢字（山・田・金）に戻す
     * ・CSSのletter-spacingを効かせた見出しは「請 求 書」のように空白が挿入されるため、空白を除去する
     *
     * いずれも表示は正しく、抽出時の見え方だけの問題である
     */
    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String raw = new PDFTextStripper().getText(document);
            return Normalizer.normalize(raw, Normalizer.Form.NFKC).replaceAll("\\s", "");
        }
    }

    private String generateText(Invoice invoice) throws IOException {
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(invoice));
        return extractText(invoicePdfService.generate(userA, 100).getContent());
    }

    // No.1
    @Test
    void generateは発行済みの請求書からPDFを生成する() throws IOException {
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(defaultIssuedInvoice()));

        InvoicePdfService.PdfDocument pdf = invoicePdfService.generate(userA, 100);

        assertThat(pdf.getContent()).isNotEmpty();
        assertThat(new String(pdf.getContent(), 0, 4)).isEqualTo("%PDF");
        assertThat(pdf.getFileName()).isEqualTo("INV-2026-0001.pdf");

        try (PDDocument document = Loader.loadPDF(pdf.getContent())) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    // No.2
    @Test
    void generateは請求書番号と取引先名と発行者名を記載する() throws IOException {
        String text = generateText(defaultIssuedInvoice());

        assertThat(text).contains("INV-2026-0001");
        assertThat(text).contains("株式会社サンプル商事");
        assertThat(text).contains("山田太郎");
    }

    // No.3
    @Test
    void generateは日本語が文字化けせず出力される() throws IOException {
        // フォント埋め込みが漏れると日本語が空になるため、固定文言の有無で検知する
        String text = generateText(defaultIssuedInvoice());

        assertThat(text).contains("請求書");
        assertThat(text).contains("合計金額");
    }

    // No.4
    @Test
    void generateは税率別内訳を税率区分ごとに記載する() throws IOException {
        Invoice invoice = issuedInvoice(List.of(
            item("Webサイト制作", "1", 10000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10),
            item("食品セット", "1", 5000, InvoiceItem.TAX_CATEGORY_TAXABLE_8, 8)));

        String text = generateText(invoice);

        assertThat(text).contains("課税10%");
        assertThat(text).contains("軽減8%");
        // 税率区分ごとに合計してから1回だけ計算した消費税額（10000×10%=1000、5000×8%=400）
        assertThat(text).contains("1,000");
        assertThat(text).contains("400");
    }

    // No.5
    @Test
    void generateは軽減税率対象の明細に注記と脚注を出力する() throws IOException {
        Invoice invoice = issuedInvoice(List.of(
            item("食品セット", "1", 5000, InvoiceItem.TAX_CATEGORY_TAXABLE_8, 8)));

        String text = generateText(invoice);

        assertThat(text).contains("※");
        assertThat(text).contains("※は軽減税率対象");
    }

    // No.6
    @Test
    void generateは軽減税率の明細が無い場合は脚注を出力しない() throws IOException {
        String text = generateText(defaultIssuedInvoice());

        assertThat(text).doesNotContain("※は軽減税率対象");
    }

    // No.7
    @Test
    void generateは登録番号が設定済みなら登録番号と適格請求書の表記を出力する() throws IOException {
        String text = generateText(defaultIssuedInvoice());

        assertThat(text).contains("T1234567890123");
        assertThat(text).contains("適格請求書");
    }

    // No.8
    @Test
    void generateは登録番号が未設定なら登録番号欄と適格請求書表記を出力しない() throws IOException {
        Invoice invoice = defaultIssuedInvoice();
        invoice.setIssuerInvoiceRegistrationNumber(null);

        String text = generateText(invoice);

        assertThat(text).doesNotContain("登録番号");
        assertThat(text).doesNotContain("適格請求書");
        // 通常の請求書としては出力される
        assertThat(text).contains("請求書");
        assertThat(text).contains("INV-2026-0001");
    }

    // No.9
    @Test
    void generateは取消済みに取消済の透かしを入れる() throws IOException {
        Invoice invoice = defaultIssuedInvoice();
        invoice.setStatus(Invoice.STATUS_CANCELED);
        invoice.setCanceledReason("金額誤りのため取消");

        String text = generateText(invoice);

        assertThat(text).contains("取消済");
    }

    // No.10
    @Test
    void generateは発行後に取引先を改名しても発行時点の記載を出力する() throws IOException {
        Invoice invoice = defaultIssuedInvoice();
        // 発行後に取引先マスターと事業者プロフィールが変わった状況を再現する
        client.setName("株式会社サンプル商事（旧サンプル商店）");

        String text = generateText(invoice);

        // PDFはスナップショット列を参照するため、発行時点の名称のまま
        // （抽出テキストは空白を除去して比較している）
        assertThat(text).contains("株式会社サンプル商事御中");
        assertThat(text).doesNotContain("旧サンプル商店");
    }

    // No.11
    @Test
    void generateは下書きのPDFを出力できない() {
        Invoice draft = defaultIssuedInvoice();
        draft.setStatus(Invoice.STATUS_DRAFT);
        draft.setInvoiceNumber(null);
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> invoicePdfService.generate(userA, 100))
            .isInstanceOf(InvalidInvoiceStateException.class);
    }

    // No.12
    @Test
    void generateは他人の請求書のPDFを出力できない() {
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoicePdfService.generate(userA, 100))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
