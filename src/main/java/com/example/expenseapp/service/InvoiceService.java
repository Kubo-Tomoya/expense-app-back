package com.example.expenseapp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.expenseapp.dto.request.InvoiceItemRequestDto;
import com.example.expenseapp.dto.request.InvoiceRequestDto;
import com.example.expenseapp.dto.response.InvoiceItemResponseDto;
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
 * 請求書関連の業務ロジック（F-17）。
 *
 * F-14と同じ設計思想で、全メソッドがuser_idによる絞り込みを前提とし、
 * 他人の請求書へのアクセスはResourceNotFoundException（404）で統一する。
 */
@Service
@Transactional
public class InvoiceService {

    // 税率区分と税率（％）の対応。表示・内訳の順序を固定したいのでLinkedHashMapで保持する
    private static final Map<String, Integer> TAX_RATES = new LinkedHashMap<>();
    static {
        TAX_RATES.put(InvoiceItem.TAX_CATEGORY_TAXABLE_10, 10);
        TAX_RATES.put(InvoiceItem.TAX_CATEGORY_TAXABLE_8, 8);
        TAX_RATES.put(InvoiceItem.TAX_CATEGORY_TAX_EXEMPT, 0);
    }

    private static final String INVOICE_NUMBER_FORMAT = "INV-%d-%04d";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberSequenceRepository sequenceRepository;
    private final ClientRepository clientRepository;
    private final BusinessProfileRepository businessProfileRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceNumberSequenceRepository sequenceRepository,
            ClientRepository clientRepository,
            BusinessProfileRepository businessProfileRepository) {
        this.invoiceRepository = invoiceRepository;
        this.sequenceRepository = sequenceRepository;
        this.clientRepository = clientRepository;
        this.businessProfileRepository = businessProfileRepository;
    }

    /**
     * 請求書一覧取得。
     * 年・ステータス・キーワードの絞り込みはフロント側で行うため（S-13の設計）、
     * ここではuser_idによる絞り込みと発行日の降順のみを担う。
     * 明細は含めない（一覧で全件の明細まで返すとペイロードが大きくなるため）
     */
    public List<InvoiceResponseDto> findAll(User user) {
        return invoiceRepository.findAllByUserIdOrderByIssueDateDesc(user.getId()).stream()
            .map(invoice -> toResponseDto(invoice, false))
            .collect(Collectors.toList());
    }

    /**
     * 請求書1件取得（詳細ドロワー・編集画面の初期値セット用）。明細を含めて返す
     */
    public InvoiceResponseDto findById(User user, Integer id) {
        return toResponseDto(findOwned(user, id), true);
    }

    /**
     * 新規作成。常に下書き（draft）として作成し、請求書番号は採番しない。
     * 取消の多い下書き段階で番号を確定させると、連番が歯抜けになるため
     */
    public InvoiceResponseDto create(User user, InvoiceRequestDto dto) {
        Client client = findActiveClient(user, dto.getClientId());

        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setStatus(Invoice.STATUS_DRAFT);
        invoice.setPaymentStatus(Invoice.PAYMENT_STATUS_UNPAID);
        invoice.setCreatedAt(LocalDateTime.now());
        applyDto(invoice, dto);

        Invoice saved = invoiceRepository.save(invoice);
        return toResponseDto(saved, true);
    }

    /**
     * 更新（下書きのみ）。
     * 発行済みは適格請求書として発行時点の記載事項を保持する必要があるため編集不可とし、
     * 訂正は取消＋再発行で行う
     */
    public InvoiceResponseDto update(User user, Integer id, InvoiceRequestDto dto) {
        Invoice invoice = findOwned(user, id);
        requireDraft(invoice, "発行済み・取消済みの請求書は編集できません。訂正する場合は取消して再発行してください");

        // 宛先を別の取引先に変更する場合のみ、有効な取引先かを検証する。
        // 同じ取引先を選び直しただけなら、既に無効化されていても更新を通す
        // （編集中の下書きの宛先が意図せず変わることを防ぐため。S-14の補足事項に対応）
        if (!invoice.getClient().getId().equals(dto.getClientId())) {
            invoice.setClient(findActiveClient(user, dto.getClientId()));
        }

        applyDto(invoice, dto);
        Invoice saved = invoiceRepository.save(invoice);
        return toResponseDto(saved, true);
    }

    /**
     * 発行。この操作で初めて請求書番号を採番し、取引先・発行者情報を確定させる。
     *
     * 汎用の更新API（PUT /api/invoices/{id}）と分離しているのは、
     * 採番という副作用と排他制御を、内容更新のロジックと混在させないため
     */
    public InvoiceResponseDto issue(User user, Integer id) {
        Invoice invoice = findOwned(user, id);
        requireDraft(invoice, "この請求書は既に発行済みまたは取消済みのため、発行できません");

        // 発行日の年を基準に採番する（発行操作日ではなく会計年度に合わせる）
        int year = invoice.getIssueDate().getYear();
        invoice.setInvoiceNumber(nextInvoiceNumber(user, year));

        // 取引先情報のスナップショット。以後、取引先を改名・住所変更しても
        // この請求書の記載内容は変わらない
        Client client = invoice.getClient();
        invoice.setClientName(client.getName());
        invoice.setClientHonorific(client.getHonorific());
        invoice.setClientAddress(client.getAddress());

        // 発行者情報のスナップショット（F-15 事業者プロフィール）。
        // 未設定でも発行自体は妨げない（PDF出力（F-18）時に不足を警告する想定）
        Optional<BusinessProfile> profile = businessProfileRepository.findByUserId(user.getId());
        profile.ifPresent(p -> {
            invoice.setIssuerBusinessName(p.getBusinessName());
            invoice.setIssuerOwnerName(p.getOwnerName());
            invoice.setIssuerAddress(p.getAddress());
            invoice.setIssuerInvoiceRegistrationNumber(p.getInvoiceRegistrationNumber());
        });

        invoice.setStatus(Invoice.STATUS_ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());

        Invoice saved = invoiceRepository.save(invoice);
        return toResponseDto(saved, true);
    }

    /**
     * 取消（発行済みのみ）。
     * レコードと請求書番号は残す。番号を欠番にすると監査時に連番の抜けを説明できず、
     * 番号を再利用すると同一番号の請求書が2枚存在してしまうため
     */
    public InvoiceResponseDto cancel(User user, Integer id, String reason) {
        Invoice invoice = findOwned(user, id);
        if (!invoice.isIssued()) {
            throw new InvalidInvoiceStateException("発行済みの請求書のみ取消できます");
        }
        invoice.setStatus(Invoice.STATUS_CANCELED);
        invoice.setCanceledAt(LocalDateTime.now());
        invoice.setCanceledReason(reason);
        invoice.setUpdatedAt(LocalDateTime.now());

        Invoice saved = invoiceRepository.save(invoice);
        return toResponseDto(saved, true);
    }

    /**
     * 入金状況の更新（F-19）。発行済みのみ操作できる。
     *
     * 下書きは発行前で入金の概念がなく、取消済みは回収対象外のため拒否する
     * （取消済みに入金を記録できると、F-20の未回収金額の集計と矛盾する）。
     *
     * 未入金への解除は冪等に扱う。既に未入金のものへ同じ指定が来ても、
     * 求めている結果は同じであるためエラーにしない
     */
    public InvoiceResponseDto updatePaymentStatus(User user, Integer id, String paymentStatus, LocalDate paidAt) {
        Invoice invoice = findOwned(user, id);
        if (!invoice.isIssued()) {
            throw new InvalidInvoiceStateException(
                "発行済みの請求書のみ入金状況を更新できます");
        }

        if (Invoice.PAYMENT_STATUS_PAID.equals(paymentStatus)) {
            invoice.setPaymentStatus(Invoice.PAYMENT_STATUS_PAID);
            // paid_atはTIMESTAMP型のため、入金日の0時として保持する
            invoice.setPaidAt(paidAt.atStartOfDay());
        } else {
            invoice.setPaymentStatus(Invoice.PAYMENT_STATUS_UNPAID);
            invoice.setPaidAt(null);
        }
        invoice.setUpdatedAt(LocalDateTime.now());

        Invoice saved = invoiceRepository.save(invoice);
        return toResponseDto(saved, true);
    }

    /**
     * 削除（下書きのみ・物理削除）。明細はON DELETE CASCADE／orphanRemovalで併せて削除される。
     * 発行済み・取消済みは監査上レコードを残す必要があるため削除できない
     */
    public void delete(User user, Integer id) {
        Invoice invoice = findOwned(user, id);
        requireDraft(invoice, "発行済み・取消済みの請求書は削除できません");
        invoiceRepository.delete(invoice);
    }

    /**
     * 明細から税率区分ごとの内訳と合計金額を算出する。
     *
     * 端数処理のルール（適格請求書の要件）：
     * ・明細金額 = 数量 × 税抜単価（1円未満切り捨て）
     * ・消費税は税率区分ごとに明細金額を合計した後、1回だけ計算して1円未満を切り捨てる
     * 　（明細ごとに端数処理すると、税率ごとに1回という要件を満たさなくなる）
     */
    public Calculation calculate(List<InvoiceItem> items) {
        Map<String, Integer> subtotalByCategory = new LinkedHashMap<>();
        TAX_RATES.keySet().forEach(category -> subtotalByCategory.put(category, 0));

        for (InvoiceItem item : items) {
            subtotalByCategory.merge(item.getTaxCategory(), item.getAmount(), Integer::sum);
        }

        List<InvoiceTaxSummaryDto> summaries = new ArrayList<>();
        int subtotalAmount = 0;
        int taxAmount = 0;

        for (Map.Entry<String, Integer> entry : subtotalByCategory.entrySet()) {
            int categorySubtotal = entry.getValue();
            if (categorySubtotal == 0) {
                continue; // 使われていない税率区分は内訳に出さない
            }
            int rate = TAX_RATES.getOrDefault(entry.getKey(), 0);
            // 税率区分ごとの合計に対して1回だけ計算し、切り捨てる
            int categoryTax = categorySubtotal * rate / 100;

            summaries.add(new InvoiceTaxSummaryDto(entry.getKey(), rate, categorySubtotal, categoryTax));
            subtotalAmount += categorySubtotal;
            taxAmount += categoryTax;
        }

        return new Calculation(subtotalAmount, taxAmount, subtotalAmount + taxAmount, summaries);
    }

    // --- 内部処理 -----------------------------------------------------------

    /**
     * 自分が所有する請求書を取得する。
     * 「存在しない」と「他人のもの」を同じ404で返すことで、
     * 存在有無から他人のIDを推測する手がかりを与えない（F-14の設計判断を踏襲）
     */
    private Invoice findOwned(User user, Integer id) {
        return invoiceRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("請求書が見つかりません。ID: " + id));
    }

    private void requireDraft(Invoice invoice, String message) {
        if (!invoice.isDraft()) {
            throw new InvalidInvoiceStateException(message);
        }
    }

    /**
     * 宛先に指定できる取引先を取得する。
     * 他人の取引先は404、無効化済みの取引先は400（InactiveClientException）で弾く
     */
    private Client findActiveClient(User user, Integer clientId) {
        Client client = clientRepository.findByIdAndUserId(clientId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("取引先が見つかりません。ID: " + clientId));
        if (Boolean.FALSE.equals(client.getIsActive())) {
            throw new InactiveClientException("無効化された取引先には請求書を作成できません");
        }
        return client;
    }

    /**
     * リクエスト内容を請求書へ反映し、金額を再計算する。
     * 金額はフロントから受け取らず必ずここで算出する（表示用の計算は入力補助と位置付ける）
     */
    private void applyDto(Invoice invoice, InvoiceRequestDto dto) {
        invoice.setIssueDate(dto.getIssueDate());
        invoice.setDueDate(dto.getDueDate() != null
            ? dto.getDueDate()
            : defaultDueDate(dto.getIssueDate()));

        invoice.replaceItems(toItems(dto.getItems()));

        Calculation calc = calculate(invoice.getItems());
        invoice.setSubtotalAmount(calc.getSubtotalAmount());
        invoice.setTaxAmount(calc.getTaxAmount());
        invoice.setTotalAmount(calc.getTotalAmount());
        invoice.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 支払期日の既定値は発行日の翌月末（S-14の仕様）
     */
    private LocalDate defaultDueDate(LocalDate issueDate) {
        return issueDate.plusMonths(1).withDayOfMonth(
            issueDate.plusMonths(1).lengthOfMonth());
    }

    private List<InvoiceItem> toItems(List<InvoiceItemRequestDto> dtos) {
        List<InvoiceItem> items = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            InvoiceItemRequestDto dto = dtos.get(i);
            InvoiceItem item = new InvoiceItem();
            // 並び順は受け取ったリストの順序をそのまま採用する（画面の表示順に意味があるため）
            item.setDisplayOrder(i);
            item.setDescription(dto.getDescription());
            item.setQuantity(dto.getQuantity());
            item.setUnitPrice(dto.getUnitPrice());
            item.setTaxCategory(dto.getTaxCategory());
            // 税率は区分から導出し、発行時点の値として明細に保持する
            item.setTaxRate(TAX_RATES.getOrDefault(dto.getTaxCategory(), 0));
            item.setAmount(itemAmount(dto.getQuantity(), dto.getUnitPrice()));
            items.add(item);
        }
        return items;
    }

    /**
     * 明細金額 = 数量 × 税抜単価。1円未満は切り捨てる
     */
    private Integer itemAmount(BigDecimal quantity, Integer unitPrice) {
        return quantity.multiply(BigDecimal.valueOf(unitPrice))
            .setScale(0, RoundingMode.FLOOR)
            .intValue();
    }

    private InvoiceResponseDto toResponseDto(Invoice invoice, boolean includeItems) {
        Calculation calc = calculate(invoice.getItems());

        List<InvoiceItemResponseDto> items = null;
        if (includeItems) {
            items = invoice.getItems().stream()
                .map(item -> new InvoiceItemResponseDto(
                    item.getId(),
                    item.getDisplayOrder(),
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTaxCategory(),
                    item.getTaxRate(),
                    item.getAmount()))
                .collect(Collectors.toList());
        }

        // 発行済みはスナップショット、下書きは現在の取引先の値を返す。
        // 画面側が状態によって参照先を切り替えなくて済むようにするため
        Client client = invoice.getClient();
        boolean hasSnapshot = invoice.getClientName() != null;

        return new InvoiceResponseDto(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            client.getId(),
            hasSnapshot ? invoice.getClientName() : client.getName(),
            hasSnapshot ? invoice.getClientHonorific() : client.getHonorific(),
            hasSnapshot ? invoice.getClientAddress() : client.getAddress(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getStatus(),
            invoice.getSubtotalAmount(),
            invoice.getTaxAmount(),
            invoice.getTotalAmount(),
            calc.getTaxSummaries(),
            items,
            invoice.getIssuerBusinessName(),
            invoice.getIssuerOwnerName(),
            invoice.getIssuerAddress(),
            invoice.getIssuerInvoiceRegistrationNumber(),
            invoice.getPaymentStatus(),
            invoice.getPaidAt(),
            invoice.getIssuedAt(),
            invoice.getCanceledAt(),
            invoice.getCanceledReason(),
            invoice.getCreatedAt()
        );
    }

    /**
     * 請求書番号を採番する。
     *
     * 該当年の採番行を排他ロックしてからインクリメントするため、
     * 同一ユーザーが同時に2件発行しても番号は重複しない。
     * 該当年の行がまだ無い場合は、last_number = 0 の行を作ってから採番する
     */
    private String nextInvoiceNumber(User user, int year) {
        InvoiceNumberSequence sequence = sequenceRepository
            .findByUserIdAndYearForUpdate(user.getId(), year)
            .orElseGet(() -> {
                InvoiceNumberSequence created = new InvoiceNumberSequence();
                created.setUserId(user.getId());
                created.setYear(year);
                created.setLastNumber(0);
                created.setUpdatedAt(LocalDateTime.now());
                return sequenceRepository.save(created);
            });

        int next = sequence.getLastNumber() + 1;
        sequence.setLastNumber(next);
        sequence.setUpdatedAt(LocalDateTime.now());
        sequenceRepository.save(sequence);

        return String.format(INVOICE_NUMBER_FORMAT, year, next);
    }

    /**
     * 金額の計算結果。合計金額と税率別内訳をまとめて返すための入れ物
     */
    public static class Calculation {
        private final Integer subtotalAmount;
        private final Integer taxAmount;
        private final Integer totalAmount;
        private final List<InvoiceTaxSummaryDto> taxSummaries;

        public Calculation(Integer subtotalAmount, Integer taxAmount, Integer totalAmount,
                List<InvoiceTaxSummaryDto> taxSummaries) {
            this.subtotalAmount = subtotalAmount;
            this.taxAmount = taxAmount;
            this.totalAmount = totalAmount;
            this.taxSummaries = taxSummaries;
        }

        public Integer getSubtotalAmount() {
            return subtotalAmount;
        }

        public Integer getTaxAmount() {
            return taxAmount;
        }

        public Integer getTotalAmount() {
            return totalAmount;
        }

        public List<InvoiceTaxSummaryDto> getTaxSummaries() {
            return taxSummaries;
        }
    }
}
