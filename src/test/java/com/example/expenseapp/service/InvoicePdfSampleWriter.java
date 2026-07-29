package com.example.expenseapp.service;

import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.expenseapp.TestcontainersConfig;
import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.Invoice;
import com.example.expenseapp.entity.InvoiceItem;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.repository.InvoiceRepository;

/**
 * レイアウト目視確認用のサンプルPDFを出力するユーティリティ（F-18）。
 *
 * 帳票の座標・余白の検証は自動化に見合わないため目視確認で代替する方針としており、
 * その確認用にPDFをファイルへ書き出す。
 *
 * 通常のテスト実行では動かないよう、システムプロパティ指定時のみ実行する：
 *   mvnw test -Dtest=InvoicePdfSampleWriter -DwritePdfSample=true
 * 出力先：target/invoice-sample.pdf（課税10%＋軽減8%＋非課税の混在）
 *         target/invoice-sample-canceled.pdf（取消済みの透かし付き）
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfSystemProperty(named = "writePdfSample", matches = "true")
class InvoicePdfSampleWriter {

    @Autowired
    private InvoicePdfService invoicePdfService;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private MailService mailService;

    @Test
    void サンプルPDFを出力する() throws Exception {
        User user = new User();
        user.setId(1);

        Client client = new Client();
        client.setId(10);
        client.setUser(user);
        client.setName("株式会社サンプル商事");
        client.setHonorific("御中");

        List<InvoiceItem> items = new ArrayList<>();
        items.add(item(0, "Webサイト制作（デザイン・実装一式）", "1", 250000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10));
        items.add(item(1, "保守作業", "0.5", 3333, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10));
        items.add(item(2, "打合せ用茶菓（軽減税率対象）", "3", 1000, InvoiceItem.TAX_CATEGORY_TAXABLE_8, 8));
        items.add(item(3, "郵送料", "1", 500, InvoiceItem.TAX_CATEGORY_TAX_EXEMPT, 0));

        Invoice invoice = new Invoice();
        invoice.setId(100);
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(Invoice.STATUS_ISSUED);
        invoice.setIssueDate(LocalDate.of(2026, 7, 28));
        invoice.setDueDate(LocalDate.of(2026, 8, 31));
        invoice.replaceItems(items);
        invoice.setClientName("株式会社サンプル商事");
        invoice.setClientHonorific("御中");
        invoice.setClientAddress("東京都千代田区丸の内1-1-1 サンプルビル10F");
        invoice.setIssuerBusinessName("サンプル工房");
        invoice.setIssuerOwnerName("山田太郎");
        invoice.setIssuerAddress("東京都新宿区西新宿2-2-2");
        invoice.setIssuerInvoiceRegistrationNumber("T1234567890123");

        InvoiceService.Calculation calc = new InvoiceService(
            invoiceRepository, null, null, null).calculate(items);
        invoice.setSubtotalAmount(calc.getSubtotalAmount());
        invoice.setTaxAmount(calc.getTaxAmount());
        invoice.setTotalAmount(calc.getTotalAmount());

        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(invoice));
        Files.write(Path.of("target/invoice-sample.pdf"),
            invoicePdfService.generate(user, 100).getContent());

        // 取消済み（透かし付き）
        invoice.setStatus(Invoice.STATUS_CANCELED);
        invoice.setCanceledReason("金額誤りのため取消");
        Files.write(Path.of("target/invoice-sample-canceled.pdf"),
            invoicePdfService.generate(user, 100).getContent());

        // 明細が1ページに収まらないケース（改ページ時のヘッダー繰り返しとページ番号の確認用）
        invoice.setStatus(Invoice.STATUS_ISSUED);
        invoice.setCanceledReason(null);
        List<InvoiceItem> manyItems = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            manyItems.add(item(i, "作業項目" + (i + 1) + "（改ページ確認用のダミー明細）",
                "1", 1000 + i, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10));
        }
        invoice.replaceItems(manyItems);
        InvoiceService.Calculation manyCalc = new InvoiceService(
            invoiceRepository, null, null, null).calculate(manyItems);
        invoice.setSubtotalAmount(manyCalc.getSubtotalAmount());
        invoice.setTaxAmount(manyCalc.getTaxAmount());
        invoice.setTotalAmount(manyCalc.getTotalAmount());
        byte[] multipage = invoicePdfService.generate(user, 100).getContent();
        Files.write(Path.of("target/invoice-sample-multipage.pdf"), multipage);

        // 改ページ・ヘッダー繰り返し・ページ番号が効いているかをログで確認できるようにする
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(multipage)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            System.out.println("[サンプル確認] 明細40行のページ数: " + doc.getNumberOfPages());
            System.out.println("[サンプル確認] 1ページ目のヘッダー出現数: "
                + text.split("税抜単価", -1).length + "（ページ数と一致すればヘッダーが繰り返されている）");
            System.out.println("[サンプル確認] ページ番号の出力: "
                + (text.contains("1 / " + doc.getNumberOfPages()) ? "あり" : "なし"));
        }
    }

    private InvoiceItem item(int order, String description, String quantity,
            int unitPrice, String taxCategory, int taxRate) {
        InvoiceItem it = new InvoiceItem();
        it.setDisplayOrder(order);
        it.setDescription(description);
        it.setQuantity(new BigDecimal(quantity));
        it.setUnitPrice(unitPrice);
        it.setTaxCategory(taxCategory);
        it.setTaxRate(taxRate);
        it.setAmount(new BigDecimal(quantity).multiply(BigDecimal.valueOf(unitPrice))
            .setScale(0, java.math.RoundingMode.FLOOR).intValue());
        return it;
    }
}
