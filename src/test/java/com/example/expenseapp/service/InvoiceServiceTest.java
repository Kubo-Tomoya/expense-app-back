package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.expenseapp.dto.request.InvoiceItemRequestDto;
import com.example.expenseapp.dto.request.InvoiceRequestDto;
import com.example.expenseapp.dto.response.InvoiceResponseDto;
import com.example.expenseapp.dto.response.InvoiceTaxSummaryDto;
import com.example.expenseapp.entity.BusinessProfile;
import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.Invoice;
import com.example.expenseapp.entity.InvoiceItem;
import com.example.expenseapp.entity.InvoiceNumberSequence;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.InactiveClientException;
import com.example.expenseapp.exception.InvalidInvoiceStateException;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.BusinessProfileRepository;
import com.example.expenseapp.repository.ClientRepository;
import com.example.expenseapp.repository.InvoiceNumberSequenceRepository;
import com.example.expenseapp.repository.InvoiceRepository;

/**
 * InvoiceServiceの単体テスト（F-17）。
 *
 * No.8〜11（端数処理）を重点的に検証している。発行後に金額を訂正できない仕様のため、
 * 消費税の計算が間違っていると取消＋再発行しか復旧手段が無くなる
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceNumberSequenceRepository sequenceRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private User userA;
    private Client activeClient;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1);
        userA.setEmail("userA@example.com");

        activeClient = new Client();
        activeClient.setId(10);
        activeClient.setUser(userA);
        activeClient.setName("株式会社サンプル商事");
        activeClient.setHonorific("御中");
        activeClient.setAddress("東京都千代田区1-1-1");
        activeClient.setIsActive(true);
    }

    private InvoiceItemRequestDto item(String description, String quantity, int unitPrice, String taxCategory) {
        InvoiceItemRequestDto dto = new InvoiceItemRequestDto();
        dto.setDescription(description);
        dto.setQuantity(new BigDecimal(quantity));
        dto.setUnitPrice(unitPrice);
        dto.setTaxCategory(taxCategory);
        return dto;
    }

    private InvoiceRequestDto request(LocalDate issueDate, LocalDate dueDate, InvoiceItemRequestDto... items) {
        InvoiceRequestDto dto = new InvoiceRequestDto();
        dto.setClientId(activeClient.getId());
        dto.setIssueDate(issueDate);
        dto.setDueDate(dueDate);
        dto.setItems(List.of(items));
        return dto;
    }

    private void mockSaveReturningArgument() {
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Invoice existingInvoice(String status, String invoiceNumber) {
        Invoice invoice = new Invoice();
        invoice.setId(100);
        invoice.setUser(userA);
        invoice.setClient(activeClient);
        invoice.setStatus(status);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setIssueDate(LocalDate.of(2026, 7, 20));
        invoice.setDueDate(LocalDate.of(2026, 8, 31));
        return invoice;
    }

    /** 明細をエンティティとして組み立てる（calculate()の直接テスト用） */
    private InvoiceItem entityItem(int amount, String taxCategory, int taxRate) {
        InvoiceItem item = new InvoiceItem();
        item.setDescription("テスト明細");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(amount);
        item.setTaxCategory(taxCategory);
        item.setTaxRate(taxRate);
        item.setAmount(amount);
        return item;
    }

    // No.4
    @Test
    void createは下書きとして作成され請求書番号は採番されない() {
        when(clientRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(activeClient));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.create(userA,
            request(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 31),
                item("Webサイト制作", "1", 100000, InvoiceItem.TAX_CATEGORY_TAXABLE_10)));

        assertThat(result.getStatus()).isEqualTo(Invoice.STATUS_DRAFT);
        assertThat(result.getInvoiceNumber()).isNull();
        // 下書きの段階では採番処理そのものが走らないことを確認する
        verify(sequenceRepository, never()).findByUserIdAndYearForUpdate(anyInt(), anyInt());
    }

    // No.5
    @Test
    void createは他人の取引先IDは指定できない() {
        when(clientRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.create(userA,
            request(LocalDate.of(2026, 7, 20), null,
                item("Webサイト制作", "1", 100000, InvoiceItem.TAX_CATEGORY_TAXABLE_10))))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(invoiceRepository, never()).save(any());
    }

    // No.6
    @Test
    void createは無効化済みの取引先は指定できない() {
        activeClient.setIsActive(false);
        when(clientRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(activeClient));

        assertThatThrownBy(() -> invoiceService.create(userA,
            request(LocalDate.of(2026, 7, 20), null,
                item("Webサイト制作", "1", 100000, InvoiceItem.TAX_CATEGORY_TAXABLE_10))))
            .isInstanceOf(InactiveClientException.class);

        verify(invoiceRepository, never()).save(any());
    }

    // No.7
    @Test
    void createは支払期日が未指定なら発行日の翌月末が設定される() {
        when(clientRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(activeClient));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.create(userA,
            request(LocalDate.of(2026, 7, 27), null,
                item("Webサイト制作", "1", 100000, InvoiceItem.TAX_CATEGORY_TAXABLE_10)));

        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    // No.8
    @Test
    void calculateは税率区分ごとに合計した後1回だけ消費税を計算する() {
        // 端数が出る単価を3件。明細ごとに切り捨てると 33+33+33 = 99 になるが、
        // 税率区分ごとに合計してから1回だけ計算すると 1000*10/100 = 100 になる
        InvoiceService.Calculation result = invoiceService.calculate(List.of(
            entityItem(333, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10),
            entityItem(333, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10),
            entityItem(334, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10)));

        assertThat(result.getSubtotalAmount()).isEqualTo(1000);
        assertThat(result.getTaxAmount()).isEqualTo(100);
        assertThat(result.getTotalAmount()).isEqualTo(1100);
    }

    // No.9
    @Test
    void calculateは10パーセントと8パーセントが混在する場合に税率ごとに内訳が分かれる() {
        InvoiceService.Calculation result = invoiceService.calculate(List.of(
            entityItem(10000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10),
            entityItem(5000, InvoiceItem.TAX_CATEGORY_TAXABLE_8, 8)));

        assertThat(result.getTaxSummaries()).hasSize(2);
        InvoiceTaxSummaryDto taxable10 = result.getTaxSummaries().get(0);
        InvoiceTaxSummaryDto taxable8 = result.getTaxSummaries().get(1);

        assertThat(taxable10.getTaxCategory()).isEqualTo(InvoiceItem.TAX_CATEGORY_TAXABLE_10);
        assertThat(taxable10.getSubtotalAmount()).isEqualTo(10000);
        assertThat(taxable10.getTaxAmount()).isEqualTo(1000);

        assertThat(taxable8.getTaxCategory()).isEqualTo(InvoiceItem.TAX_CATEGORY_TAXABLE_8);
        assertThat(taxable8.getSubtotalAmount()).isEqualTo(5000);
        assertThat(taxable8.getTaxAmount()).isEqualTo(400);

        assertThat(result.getSubtotalAmount()).isEqualTo(15000);
        assertThat(result.getTaxAmount()).isEqualTo(1400);
    }

    // No.10
    @Test
    void calculateは非課税明細のみの場合に消費税が0になる() {
        InvoiceService.Calculation result = invoiceService.calculate(List.of(
            entityItem(20000, InvoiceItem.TAX_CATEGORY_TAX_EXEMPT, 0)));

        assertThat(result.getTaxAmount()).isZero();
        assertThat(result.getTotalAmount()).isEqualTo(result.getSubtotalAmount());
    }

    // No.11
    @Test
    void createは数量が小数の場合も明細金額が1円未満切り捨てになる() {
        when(clientRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(activeClient));
        mockSaveReturningArgument();

        // 0.5 × 3333 = 1666.5 → 1666
        InvoiceResponseDto result = invoiceService.create(userA,
            request(LocalDate.of(2026, 7, 20), null,
                item("保守作業（0.5人日）", "0.5", 3333, InvoiceItem.TAX_CATEGORY_TAXABLE_10)));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAmount()).isEqualTo(1666);
        assertThat(result.getSubtotalAmount()).isEqualTo(1666);
    }

    // No.12
    @Test
    void issueは発行日の年で連番が採番される() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));
        when(sequenceRepository.findByUserIdAndYearForUpdate(1, 2026)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(InvoiceNumberSequence.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.empty());
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.issue(userA, 100);

        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2026-0001");
        assertThat(result.getStatus()).isEqualTo(Invoice.STATUS_ISSUED);
        assertThat(result.getIssuedAt()).isNotNull();
    }

    // No.13
    @Test
    void issueは2件目の発行で連番がインクリメントされる() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        InvoiceNumberSequence sequence = new InvoiceNumberSequence();
        sequence.setUserId(1);
        sequence.setYear(2026);
        sequence.setLastNumber(1); // 既に1件発行済み

        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));
        when(sequenceRepository.findByUserIdAndYearForUpdate(1, 2026)).thenReturn(Optional.of(sequence));
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.empty());
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.issue(userA, 100);

        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2026-0002");
        assertThat(sequence.getLastNumber()).isEqualTo(2);
    }

    // No.14
    @Test
    void issueは年が変わると連番が0001に戻る() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        draft.setIssueDate(LocalDate.of(2027, 1, 15));

        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));
        // 2027年の採番実績はまだ無い
        when(sequenceRepository.findByUserIdAndYearForUpdate(1, 2027)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(InvoiceNumberSequence.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.empty());
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.issue(userA, 100);

        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2027-0001");
    }

    // No.15
    @Test
    void issueは取引先と発行者情報がスナップショットされる() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);

        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessName("サンプル工房");
        profile.setOwnerName("久保智也");
        profile.setAddress("東京都新宿区2-2-2");
        profile.setInvoiceRegistrationNumber("T1234567890123");

        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));
        when(sequenceRepository.findByUserIdAndYearForUpdate(1, 2026)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(InvoiceNumberSequence.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.of(profile));
        mockSaveReturningArgument();

        invoiceService.issue(userA, 100);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        Invoice saved = captor.getValue();

        assertThat(saved.getClientName()).isEqualTo("株式会社サンプル商事");
        assertThat(saved.getClientHonorific()).isEqualTo("御中");
        assertThat(saved.getClientAddress()).isEqualTo("東京都千代田区1-1-1");
        assertThat(saved.getIssuerBusinessName()).isEqualTo("サンプル工房");
        assertThat(saved.getIssuerOwnerName()).isEqualTo("久保智也");
        assertThat(saved.getIssuerInvoiceRegistrationNumber()).isEqualTo("T1234567890123");
    }

    // No.16
    @Test
    void 発行後に取引先を改名しても請求書の記載内容は変わらない() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);

        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));
        when(sequenceRepository.findByUserIdAndYearForUpdate(1, 2026)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(InvoiceNumberSequence.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.empty());
        mockSaveReturningArgument();

        invoiceService.issue(userA, 100);

        // 発行後に取引先マスターを改名する
        activeClient.setName("株式会社サンプル商事（旧サンプル商店）");

        InvoiceResponseDto reloaded = invoiceService.findById(userA, 100);

        // スナップショットを参照するため、発行時点の名称のまま
        assertThat(reloaded.getClientName()).isEqualTo("株式会社サンプル商事");
    }

    // No.17
    @Test
    void issueは発行済みを再発行できない() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> invoiceService.issue(userA, 100))
            .isInstanceOf(InvalidInvoiceStateException.class);

        // 番号の再採番が行われないことを確認する
        verify(sequenceRepository, never()).findByUserIdAndYearForUpdate(anyInt(), anyInt());
        verify(invoiceRepository, never()).save(any());
    }

    // No.18
    @Test
    void updateは発行済みを編集できない() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> invoiceService.update(userA, 100,
            request(LocalDate.of(2026, 7, 20), null,
                item("差し替え明細", "1", 50000, InvoiceItem.TAX_CATEGORY_TAXABLE_10))))
            .isInstanceOf(InvalidInvoiceStateException.class);

        verify(invoiceRepository, never()).save(any());
    }

    // No.19
    @Test
    void updateは下書きの明細を全行差し替えて更新できる() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        draft.replaceItems(List.of(
            entityItem(1000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10),
            entityItem(2000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10),
            entityItem(3000, InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10)));

        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.update(userA, 100,
            request(LocalDate.of(2026, 7, 20), null,
                item("設計作業", "1", 40000, InvoiceItem.TAX_CATEGORY_TAXABLE_10),
                item("実装作業", "2", 30000, InvoiceItem.TAX_CATEGORY_TAXABLE_10)));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting("description")
            .containsExactly("設計作業", "実装作業");
        assertThat(result.getSubtotalAmount()).isEqualTo(100000);
    }

    // No.20
    @Test
    void cancelは発行済みを取消でき請求書番号は残る() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.cancel(userA, 100, "金額誤りのため取消");

        assertThat(result.getStatus()).isEqualTo(Invoice.STATUS_CANCELED);
        assertThat(result.getCanceledReason()).isEqualTo("金額誤りのため取消");
        assertThat(result.getCanceledAt()).isNotNull();
        // 番号を欠番にしないため、取消後も番号は保持される
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2026-0001");
    }

    // No.21
    @Test
    void cancelは下書きを取消できない() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> invoiceService.cancel(userA, 100, "誤作成のため取消"))
            .isInstanceOf(InvalidInvoiceStateException.class);

        verify(invoiceRepository, never()).save(any());
    }

    // No.22
    @Test
    void deleteは下書きを削除できる() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));

        invoiceService.delete(userA, 100);

        verify(invoiceRepository).delete(draft);
    }

    // No.23
    @Test
    void deleteは発行済みを削除できない() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> invoiceService.delete(userA, 100))
            .isInstanceOf(InvalidInvoiceStateException.class);

        verify(invoiceRepository, never()).delete(any());
    }

    // --- F-19 入金管理 ------------------------------------------------------

    // F-19 No.1
    @Test
    void updatePaymentStatusは発行済みを入金済みにでき入金日が記録される() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_PAID, LocalDate.of(2026, 8, 5));

        assertThat(result.getPaymentStatus()).isEqualTo(Invoice.PAYMENT_STATUS_PAID);
        // paid_atはTIMESTAMP型のため、入金日の0時として保持される
        assertThat(result.getPaidAt()).isEqualTo(LocalDate.of(2026, 8, 5).atStartOfDay());
    }

    // F-19 No.2
    @Test
    void updatePaymentStatusは入金済みを未入金に戻せる() {
        Invoice paid = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        paid.setPaymentStatus(Invoice.PAYMENT_STATUS_PAID);
        paid.setPaidAt(LocalDate.of(2026, 8, 5).atStartOfDay());
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(paid));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_UNPAID, null);

        assertThat(result.getPaymentStatus()).isEqualTo(Invoice.PAYMENT_STATUS_UNPAID);
        assertThat(result.getPaidAt()).isNull();
    }

    // F-19 No.3
    @Test
    void updatePaymentStatusは既に未入金のものを解除しても状態が変わらない() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));
        mockSaveReturningArgument();

        // 求めている結果は同じであるため、冪等に扱いエラーとしない
        InvoiceResponseDto result = invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_UNPAID, null);

        assertThat(result.getPaymentStatus()).isEqualTo(Invoice.PAYMENT_STATUS_UNPAID);
        assertThat(result.getPaidAt()).isNull();
    }

    // F-19 No.4
    @Test
    void updatePaymentStatusは請求書番号とステータスに影響しない() {
        Invoice issued = existingInvoice(Invoice.STATUS_ISSUED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(issued));
        mockSaveReturningArgument();

        InvoiceResponseDto result = invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_PAID, LocalDate.of(2026, 8, 5));

        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2026-0001");
        assertThat(result.getStatus()).isEqualTo(Invoice.STATUS_ISSUED);
    }

    // F-19 No.5
    @Test
    void updatePaymentStatusは下書きの入金状況を更新できない() {
        Invoice draft = existingInvoice(Invoice.STATUS_DRAFT, null);
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_PAID, LocalDate.of(2026, 8, 5)))
            .isInstanceOf(InvalidInvoiceStateException.class);

        verify(invoiceRepository, never()).save(any());
    }

    // F-19 No.6
    @Test
    void updatePaymentStatusは取消済みの入金状況を更新できない() {
        Invoice canceled = existingInvoice(Invoice.STATUS_CANCELED, "INV-2026-0001");
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(canceled));

        assertThatThrownBy(() -> invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_PAID, LocalDate.of(2026, 8, 5)))
            .isInstanceOf(InvalidInvoiceStateException.class);

        verify(invoiceRepository, never()).save(any());
    }

    // F-19 No.7
    @Test
    void updatePaymentStatusは他人の請求書を更新できない() {
        when(invoiceRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.updatePaymentStatus(
            userA, 100, Invoice.PAYMENT_STATUS_PAID, LocalDate.of(2026, 8, 5)))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(invoiceRepository, never()).save(any());
    }
}
