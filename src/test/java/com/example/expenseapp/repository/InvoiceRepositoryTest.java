package com.example.expenseapp.repository;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.Invoice;
import com.example.expenseapp.entity.InvoiceItem;
import com.example.expenseapp.entity.InvoiceNumberSequence;
import com.example.expenseapp.entity.User;

/**
 * InvoiceRepository・InvoiceNumberSequenceRepositoryのRepository層テスト（F-17）。
 * ClientRepositoryTestと同じくTestcontainersを使用する
 */
@DataJpaTest
@Testcontainers
class InvoiceRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceNumberSequenceRepository sequenceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User userA;
    private User userB;
    private Client clientA;
    private Client clientB;

    @BeforeEach
    void setUp() {
        userA = createUser("userA@example.com");
        userB = createUser("userB@example.com");
        clientA = createClient(userA, "株式会社サンプル商事");
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

    private Invoice createInvoice(User user, Client client, LocalDate issueDate, String invoiceNumber) {
        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(issueDate.plusMonths(1));
        invoice.setStatus(invoiceNumber == null ? Invoice.STATUS_DRAFT : Invoice.STATUS_ISSUED);
        invoice.setSubtotalAmount(10000);
        invoice.setTaxAmount(1000);
        invoice.setTotalAmount(11000);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());

        InvoiceItem item = new InvoiceItem();
        item.setDisplayOrder(0);
        item.setDescription("Webサイト制作");
        item.setQuantity(new BigDecimal("1.00"));
        item.setUnitPrice(10000);
        item.setTaxCategory(InvoiceItem.TAX_CATEGORY_TAXABLE_10);
        item.setTaxRate(10);
        item.setAmount(10000);
        invoice.replaceItems(List.of(item));

        return invoiceRepository.save(invoice);
    }

    // No.1
    @Test
    void findAllByUserIdOrderByIssueDateDescは自分の請求書のみを発行日の降順で返す() {
        createInvoice(userA, clientA, LocalDate.of(2026, 5, 10), "INV-2026-0001");
        createInvoice(userA, clientA, LocalDate.of(2026, 7, 20), "INV-2026-0002");
        createInvoice(userB, clientB, LocalDate.of(2026, 6, 15), "INV-2026-0001");

        List<Invoice> result = invoiceRepository.findAllByUserIdOrderByIssueDateDesc(userA.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Invoice::getIssueDate)
            .containsExactly(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 5, 10));
    }

    // No.2
    @Test
    void findByIdAndUserIdは他人の請求書は取得できない() {
        Invoice saved = createInvoice(userA, clientA, LocalDate.of(2026, 7, 20), "INV-2026-0001");

        Optional<Invoice> result = invoiceRepository.findByIdAndUserId(saved.getId(), userB.getId());

        assertThat(result).isEmpty();
    }

    // No.3
    @Test
    void findByUserIdAndYearForUpdateは該当行を排他ロックして取得できる() {
        InvoiceNumberSequence sequence = new InvoiceNumberSequence();
        sequence.setUserId(userA.getId());
        sequence.setYear(2026);
        sequence.setLastNumber(3);
        sequence.setUpdatedAt(LocalDateTime.now());
        sequenceRepository.save(sequence);
        entityManager.flush();

        Optional<InvoiceNumberSequence> result =
            sequenceRepository.findByUserIdAndYearForUpdate(userA.getId(), 2026);

        assertThat(result).isPresent();
        assertThat(result.get().getLastNumber()).isEqualTo(3);
    }

    // No.30
    @Test
    void 異なるユーザー間では同一の請求書番号が共存できる() {
        createInvoice(userA, clientA, LocalDate.of(2026, 7, 20), "INV-2026-0001");
        createInvoice(userB, clientB, LocalDate.of(2026, 7, 20), "INV-2026-0001");

        entityManager.flush();

        // ユーザーごとに1件ずつ、同じ番号で共存できていることを確認する
        assertThat(invoiceRepository.findAllByUserIdOrderByIssueDateDesc(userA.getId())).hasSize(1);
        assertThat(invoiceRepository.findAllByUserIdOrderByIssueDateDesc(userB.getId())).hasSize(1);
    }

    // No.31
    @Test
    void 同一ユーザー内では同一の請求書番号を作成できない() {
        createInvoice(userA, clientA, LocalDate.of(2026, 7, 20), "INV-2026-0001");

        // 主キーがIDENTITY採番のため、save()の時点でINSERTが実行され制約違反が検知される
        assertThatThrownBy(() ->
            createInvoice(userA, clientA, LocalDate.of(2026, 7, 21), "INV-2026-0001"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // No.32
    @Test
    void DB直接操作でも不正なstatusは拒否される() {
        // アプリ側の@Patternを経由しない経路（SQL直接実行）でも、
        // DBのCHECK制約で弾かれることを確認する
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                "INSERT INTO invoices (user_id, client_id, issue_date, due_date, status) " +
                "VALUES (:userId, :clientId, DATE '2026-07-20', DATE '2026-08-31', 'invalid')")
                .setParameter("userId", userA.getId())
                .setParameter("clientId", clientA.getId())
                .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
