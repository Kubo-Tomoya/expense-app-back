package com.example.expenseapp.repository;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.Invoice;
import com.example.expenseapp.entity.InvoiceItem;
import com.example.expenseapp.entity.User;

/**
 * F-20（収支ダッシュボード拡張）の集計クエリのテスト。
 *
 * 集計はSQL側で行うため、Mockitoではなく実DB（Testcontainers）で検証する。
 * 経費側の集計は既存のGET /api/expenses/summary（F-08・F-27でテスト済み）を使うため、
 * ここでは請求書側の集計のみを対象とする
 */
@DataJpaTest
@Testcontainers
class InvoiceSummaryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;
    private Client clientA;
    private Client clientB;

    @BeforeEach
    void setUp() {
        userA = createUser("userA@example.com");
        userB = createUser("userB@example.com");
        clientA = createClient(userA, "株式会社アルファ");
        clientB = createClient(userB, "userBの取引先");
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("dummy-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private Client createClient(User user, String name) {
        Client client = new Client();
        client.setUser(user);
        client.setName(name);
        client.setHonorific("御中");
        client.setIsActive(true);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        return clientRepository.save(client);
    }

    /**
     * 請求書を1件作る。金額は税抜単価から税込を組み立てる（10%固定）
     */
    private Invoice createInvoice(User user, Client client, String status, String paymentStatus,
            LocalDate issueDate, LocalDate dueDate, int unitPriceExcludingTax) {
        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setStatus(status);
        invoice.setPaymentStatus(paymentStatus);
        invoice.setInvoiceNumber(Invoice.STATUS_DRAFT.equals(status)
            ? null
            : "INV-" + issueDate.getYear() + "-" + String.format("%04d", (int) (Math.random() * 9000 + 1000)));
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);

        int tax = unitPriceExcludingTax / 10;
        invoice.setSubtotalAmount(unitPriceExcludingTax);
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(unitPriceExcludingTax + tax);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());

        InvoiceItem item = new InvoiceItem();
        item.setDisplayOrder(0);
        item.setDescription("テスト明細");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(unitPriceExcludingTax);
        item.setTaxCategory(InvoiceItem.TAX_CATEGORY_TAXABLE_10);
        item.setTaxRate(10);
        item.setAmount(unitPriceExcludingTax);
        invoice.replaceItems(List.of(item));

        return invoiceRepository.save(invoice);
    }

    // No.1
    @Test
    void 売上は発行済みのみが計上される() {
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 31), 10000);
        createInvoice(userA, clientA, Invoice.STATUS_DRAFT, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 11), LocalDate.of(2026, 8, 31), 50000);
        createInvoice(userA, clientA, Invoice.STATUS_CANCELED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 31), 70000);

        Integer sales = invoiceRepository.sumSalesByUserIdAndYearMonth(userA.getId(), 2026, 7);

        // 下書き・取消済みは含まないため、発行済みの11,000円のみ
        assertThat(sales).isEqualTo(11000);
    }

    // No.2
    @Test
    void 売上は発行日ベースで対象月が絞られる() {
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 31), 10000);
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31), 20000);
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30), 30000);

        assertThat(invoiceRepository.sumSalesByUserIdAndYearMonth(userA.getId(), 2026, 7)).isEqualTo(22000);
    }

    // No.3
    @Test
    void 売上は税込で集計される() {
        // 税抜10,000＋消費税1,000＝税込11,000
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 31), 10000);

        assertThat(invoiceRepository.sumSalesByUserIdAndYearMonth(userA.getId(), 2026, 7)).isEqualTo(11000);
    }

    // No.4
    @Test
    void 未回収金額は発行済みかつ未入金のみを対象とする() {
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 31), 10000);
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_PAID,
            LocalDate.of(2026, 7, 11), LocalDate.of(2026, 8, 31), 50000);

        assertThat(invoiceRepository.sumUnpaidByUserId(userA.getId())).isEqualTo(11000);
    }

    // No.5
    @Test
    void 未回収金額に取消済みは含まれない() {
        createInvoice(userA, clientA, Invoice.STATUS_CANCELED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 31), 10000);

        assertThat(invoiceRepository.sumUnpaidByUserId(userA.getId())).isZero();
    }

    // No.6
    @Test
    void 期日超過は未入金かつ支払期日が当日より前のものだけを対象とする() {
        LocalDate today = LocalDate.now();
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            today.minusMonths(2), today.minusDays(1), 10000); // 期日超過
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            today.minusMonths(1), today.plusDays(10), 50000); // 期日内

        assertThat(invoiceRepository.sumOverdueByUserId(userA.getId(), today)).isEqualTo(11000);
    }

    // No.7
    @Test
    void 期日超過に入金済みは含まれない() {
        LocalDate today = LocalDate.now();
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_PAID,
            today.minusMonths(2), today.minusDays(1), 10000);

        assertThat(invoiceRepository.sumOverdueByUserId(userA.getId(), today)).isZero();
    }

    // No.8
    @Test
    void 他ユーザーの請求書は集計に含まれない() {
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 31), 10000);
        createInvoice(userB, clientB, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 31), 90000);

        assertThat(invoiceRepository.sumSalesByUserIdAndYearMonth(userA.getId(), 2026, 7)).isEqualTo(11000);
        assertThat(invoiceRepository.sumUnpaidByUserId(userA.getId())).isEqualTo(11000);
    }

    // No.9
    @Test
    void 対象月に請求書が0件の場合は0が返る() {
        assertThat(invoiceRepository.sumSalesByUserIdAndYearMonth(userA.getId(), 2026, 7)).isZero();
        assertThat(invoiceRepository.sumUnpaidByUserId(userA.getId())).isZero();
        assertThat(invoiceRepository.sumOverdueByUserId(userA.getId(), LocalDate.now())).isZero();
    }

    // No.10
    @Test
    void 月別売上はデータがある月のみ行が返る() {
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 30), 10000);
        createInvoice(userA, clientA, Invoice.STATUS_ISSUED, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 31), 20000);
        // 下書きは月別売上にも含めない
        createInvoice(userA, clientA, Invoice.STATUS_DRAFT, Invoice.PAYMENT_STATUS_UNPAID,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30), 30000);

        List<Object[]> rows = invoiceRepository.findMonthlySalesByUserIdAndYear(userA.getId(), 2026);

        // Service側で12ヶ月分に0埋めするため、ここではデータのある月だけが返ることを確認する
        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0)[0]).intValue()).isEqualTo(3);
        assertThat(((Number) rows.get(0)[1]).intValue()).isEqualTo(11000);
        assertThat(((Number) rows.get(1)[0]).intValue()).isEqualTo(7);
        assertThat(((Number) rows.get(1)[1]).intValue()).isEqualTo(22000);
    }
}
