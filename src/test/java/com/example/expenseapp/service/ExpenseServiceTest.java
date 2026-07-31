package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.expenseapp.dto.request.ExpenseRequestDto;
import com.example.expenseapp.dto.response.ExpenseResponseDto;
import com.example.expenseapp.dto.response.SummaryResponseDto;
import com.example.expenseapp.dto.response.YearSummaryResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.Expense;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.InactiveCategoryException;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.CategoryRepository;
import com.example.expenseapp.repository.ExpenseRepository;

/**
 * ExpenseServiceのService層テスト。
 *
 * @DataJpaTestとは異なり、DBには一切接続しない。
 * ExpenseRepository・CategoryRepositoryをMockito（@Mock）で模擬し、
 * 「他人のデータを検索した想定＝Optional.empty()を返す」という状況を
 * 意図的に作り出すことで、Service層のロジックだけを高速に検証する
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private User userA;
    private Category categoryA;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1);
        userA.setEmail("userA@example.com");

        categoryA = new Category();
        categoryA.setId(10);
        categoryA.setUser(userA);
        categoryA.setName("交通費");
    }

    @Test
    void findByIdは自分の経費を取得できる() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);
        expense.setCategory(categoryA);
        expense.setTitle("新幹線代");
        expense.setAmount(12500);
        expense.setExpenseDate(LocalDate.of(2026, 7, 10));
        expense.setStatus("registered");

        // 「userAのID(1)でexpenseId=100を検索したら、この経費が見つかる」という状況をMock化
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));

        ExpenseResponseDto result = expenseService.findById(userA, 100);

        assertThat(result.getTitle()).isEqualTo("新幹線代");
    }

    @Test
    void findByIdは他人の経費だとResourceNotFoundExceptionを投げる() {
        // Repositoryが「見つからなかった(Optional.empty())」を返す状況を模擬する。
        // これは実際には「他人の経費だから絞り込み条件に一致しなかった」ケースに相当する
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.findById(userA, 100))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("経費が見つかりません");
    }

    @Test
    void updateは他人の経費だとResourceNotFoundExceptionを投げ更新処理を実行しない() {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("不正アクセステスト");
        dto.setAmount(1000);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 20));

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.update(userA, 100, dto))
            .isInstanceOf(ResourceNotFoundException.class);

        // 例外が投げられた時点で処理が中断され、
        // カテゴリ検索やsave()が一切呼ばれていないことを確認する
        // （F-04動作確認時に手動で行った「他人のデータが実際に書き換わっていないか」の
        //   確認を、自動テストとして再現している）
        verify(categoryRepository, never()).findByIdAndUserId(anyInt(), anyInt());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateは他人のカテゴリを指定するとResourceNotFoundExceptionを投げる() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);
        expense.setCategory(categoryA);
        expense.setTitle("元のタイトル");
        expense.setAmount(5000);
        expense.setExpenseDate(LocalDate.of(2026, 7, 1));
        expense.setStatus("registered");

        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("更新後タイトル");
        dto.setAmount(6000);
        dto.setCategoryId(999); // 他人のカテゴリID
        dto.setExpenseDate(LocalDate.of(2026, 7, 20));

        // 経費自体は自分のものなので見つかるが、指定したカテゴリIDは他人のものなので見つからない
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));
        when(categoryRepository.findByIdAndUserId(999, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.update(userA, 100, dto))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("カテゴリが見つかりません");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void deleteは他人の経費だとResourceNotFoundExceptionを投げ削除処理を実行しない() {
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.delete(userA, 100))
            .isInstanceOf(ResourceNotFoundException.class);

        // deleteが呼ばれていないことまで確認する（例外を投げた後に
        // 誤って削除処理まで進んでしまうことがないかの確認）
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void deleteは自分の経費を削除できる() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));

        expenseService.delete(userA, 100);

        // 正しい経費が削除処理に渡されたことを確認する
        verify(expenseRepository, times(1)).delete(expense);
    }
    
    /**
     * No.1：statusにdraftを指定して登録できる
     */
    @Test
    void createはstatusにdraftを指定して登録できる() {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("来月の会食（見込み）");
        dto.setAmount(5000);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 8, 1));
        dto.setStatus("draft");

        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(categoryA));
        // save()に渡されたEntityをそのまま返すよう設定し、setStatus等の結果を検証できるようにする
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getStatus()).isEqualTo("draft");
    }

    /**
     * No.2：statusにdraftを指定して更新できる
     */
    @Test
    void updateはstatusにdraftを指定して更新できる() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);
        expense.setCategory(categoryA);
        expense.setTitle("元のタイトル");
        expense.setAmount(5000);
        expense.setExpenseDate(LocalDate.of(2026, 7, 1));
        expense.setStatus("registered"); // 元は確定済み

        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("元のタイトル");
        dto.setAmount(5000);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 1));
        dto.setStatus("draft"); // 下書きに変更

        // カテゴリを変更していないため、F-28以降はカテゴリの再取得を行わない
        // （同じカテゴリを選び直した場合は無効化済みでも通す仕様のため）
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponseDto result = expenseService.update(userA, 100, dto);

        assertThat(result.getStatus()).isEqualTo("draft");
    }

    /**
     * No.4：下書き分は確定集計から除外される
     *
     * findSummaryByUserIdAndYearAndStatusを、statusの値に応じて
     * 異なる結果を返すようMock化することで、「確定分」と「下書き分」を
     * 正しく別々に集計しているかを検証する
     */
    @Test
    void getSummaryは下書き分を確定集計から除外しdraftAmountに計上する() {
        // 確定分(registered)：交通費 10,000円
        List<Object[]> confirmedRows = new ArrayList<>();
        confirmedRows.add(new Object[]{7, "交通費", 10000});

        // 下書き分(draft)：食費 3,000円
        List<Object[]> draftRows = new ArrayList<>();
        draftRows.add(new Object[]{7, "食費", 3000});

        when(expenseRepository.findSummaryByUserIdAndYearAndStatus(1, 2026, "registered"))
            .thenReturn(confirmedRows);
        when(expenseRepository.findSummaryByUserIdAndYearAndStatus(1, 2026, "draft"))
            .thenReturn(draftRows);

        SummaryResponseDto result = expenseService.getSummary(userA, 2026, 7);

        assertThat(result.getTotalAmount()).isEqualTo(10000);
        assertThat(result.getCategoryBreakdown()).containsEntry("交通費", 10000);
        assertThat(result.getCategoryBreakdown()).doesNotContainKey("食費");
        assertThat(result.getDraftAmount()).isEqualTo(3000);
    }

    /**
     * No.5：下書き分は年間集計から除外される（リグレッション防止）
     *
     * findSummaryByUserIdAndYear（年間集計用）が、既にRepository側でregistered限定に
     * 絞り込まれている前提のため、Service側では「Repositoryが返した結果を
     * そのまま正しく積み上げているか」を検証する。
     * Repository自体がregisteredのみを返すことは、別途CategoryRepositoryTest等と
     * 同様の形でRepository層のテストとして検証すべき項目である
     */
    @Test
    void getYearlySummaryはRepositoryが返した確定分のみを正しく集計する() {
        // 修正前： List<Object[]> rows = List.of(new Object[]{7, "交通費", 10000}, new Object[]{8, "食費", 5000});
        // 修正後：
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{7, "交通費", 10000});
        rows.add(new Object[]{8, "食費", 5000});

        when(expenseRepository.findSummaryByUserIdAndYear(1, 2026)).thenReturn(rows);

        YearSummaryResponseDto result = expenseService.getYearlySummary(userA, 2026);

        assertThat(result.getTotalAmount()).isEqualTo(15000);
        assertThat(result.getMonthly()).hasSize(12);
    }
    
 // ===== F-01：経費登録（create） =====

    @Test
    void createは正しい入力で経費が登録される() {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("新幹線代");
        dto.setAmount(12500);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 10));
        dto.setStatus("registered");

        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(categoryA));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(200);
            return expense;
        });

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getId()).isEqualTo(200);
        assertThat(result.getTitle()).isEqualTo("新幹線代");
        assertThat(result.getAmount()).isEqualTo(12500);
    }

    @Test
    void createは他ユーザーのカテゴリIDを指定すると登録できない() {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("不正アクセステスト");
        dto.setAmount(1000);
        dto.setCategoryId(999); // 他ユーザーのカテゴリID
        dto.setExpenseDate(LocalDate.of(2026, 7, 10));
        dto.setStatus("registered");

        when(categoryRepository.findByIdAndUserId(999, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.create(userA, dto))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("カテゴリが見つかりません");

        // カテゴリが見つからない時点で処理が中断され、save()が呼ばれないことを確認
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createは金額1円ちょうどで登録できる() {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("最小金額テスト");
        dto.setAmount(1); // 境界値：1円ちょうど
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 10));
        dto.setStatus("registered");

        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(categoryA));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getAmount()).isEqualTo(1);
    }

    // ===== F-02：経費一覧表示（findAll） =====

    @Test
    void findAllは月指定時にその月の経費のみ取得できる() {
        Expense expense = new Expense();
        expense.setId(300);
        expense.setUser(userA);
        expense.setCategory(categoryA);
        expense.setTitle("7月の経費");
        expense.setAmount(5000);
        expense.setExpenseDate(LocalDate.of(2026, 7, 15));
        expense.setStatus("registered");

        when(expenseRepository.findByUserIdAndMonth(1, "2026-07")).thenReturn(List.of(expense));

        List<ExpenseResponseDto> result = expenseService.findAll(userA, "2026-07");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("7月の経費");
        // 月指定時はfindByUserIdAndMonthが呼ばれ、findAllByUserIdOrderByExpenseDateDescは呼ばれないことを確認
        verify(expenseRepository, never()).findAllByUserIdOrderByExpenseDateDesc(anyInt());
    }

    @Test
    void findAllは月未指定時は全件取得できる() {
        Expense expense1 = new Expense();
        expense1.setId(301);
        expense1.setUser(userA);
        expense1.setCategory(categoryA);
        expense1.setTitle("経費1");
        expense1.setAmount(1000);
        expense1.setExpenseDate(LocalDate.of(2026, 5, 1));
        expense1.setStatus("registered");

        Expense expense2 = new Expense();
        expense2.setId(302);
        expense2.setUser(userA);
        expense2.setCategory(categoryA);
        expense2.setTitle("経費2");
        expense2.setAmount(2000);
        expense2.setExpenseDate(LocalDate.of(2026, 7, 1));
        expense2.setStatus("registered");

        when(expenseRepository.findAllByUserIdOrderByExpenseDateDesc(1)).thenReturn(List.of(expense2, expense1));

        List<ExpenseResponseDto> result = expenseService.findAll(userA, null);

        assertThat(result).hasSize(2);
        // 月指定なしの場合、findByUserIdAndMonthは呼ばれないことを確認
        verify(expenseRepository, never()).findByUserIdAndMonth(anyInt(), any());
    }

    @Test
    void findAllは他ユーザーの経費が含まれない() {
        // Repositoryが「userA分のみ」を返す状況をMock化することで、
        // Serviceがuser.getId()を正しく渡していることを確認する
        Expense expense = new Expense();
        expense.setId(303);
        expense.setUser(userA);
        expense.setCategory(categoryA);
        expense.setTitle("userAの経費");
        expense.setAmount(1000);
        expense.setExpenseDate(LocalDate.of(2026, 7, 1));
        expense.setStatus("registered");

        when(expenseRepository.findAllByUserIdOrderByExpenseDateDesc(1)).thenReturn(List.of(expense));

        List<ExpenseResponseDto> result = expenseService.findAll(userA, null);

        assertThat(result).hasSize(1);
        // userAのID(1)で絞り込みが行われたことを確認（他ユーザーIDでは呼ばれていない）
        verify(expenseRepository).findAllByUserIdOrderByExpenseDateDesc(1);
    }

    // ===== F-03：経費編集（update・正常系のみ、異常系は既存の重複テストを参照） =====

    @Test
    void updateは正しい入力で経費が更新される() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);
        expense.setCategory(categoryA);
        expense.setTitle("元のタイトル");
        expense.setAmount(5000);
        expense.setExpenseDate(LocalDate.of(2026, 7, 1));
        expense.setStatus("registered");

        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("更新後タイトル");
        dto.setAmount(8000);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 15));
        dto.setStatus("registered");

        // カテゴリを変更していないため、F-28以降はカテゴリの再取得を行わない
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponseDto result = expenseService.update(userA, 100, dto);

        assertThat(result.getTitle()).isEqualTo("更新後タイトル");
        assertThat(result.getAmount()).isEqualTo(8000);
    }

    // ===== F-05：領収書アップロード（uploadReceipt） =====
    @Test
    void uploadReceiptはJPEGファイルが正常にアップロードできる() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);
        expense.setCategory(categoryA); // toResponseDto内でcategory.getName()が呼ばれるため必須

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "receipt.jpg", "image/jpeg", "dummy-image-content".getBytes());

        ExpenseResponseDto result = expenseService.uploadReceipt(userA, 100, file);

        assertThat(result.getReceiptImagePath()).isNotNull();
        assertThat(result.getReceiptImagePath()).contains("receipt_100_");
    }

    @Test
    void uploadReceiptは不正な形式のファイルは拒否される() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "document.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        assertThatThrownBy(() -> expenseService.uploadReceipt(userA, 100, file))
            .isInstanceOf(com.example.expenseapp.exception.InvalidFileException.class)
            .hasMessageContaining("JPEGまたはPNG形式");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void uploadReceiptは5MB超のファイルは拒否される() {
        Expense expense = new Expense();
        expense.setId(100);
        expense.setUser(userA);

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));

        // 5MB(5 * 1024 * 1024 byte)を超えるダミーデータを作成する
        byte[] largeContent = new byte[6 * 1024 * 1024];
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "large.jpg", "image/jpeg", largeContent);

        assertThatThrownBy(() -> expenseService.uploadReceipt(userA, 100, file))
            .isInstanceOf(com.example.expenseapp.exception.InvalidFileException.class)
            .hasMessageContaining("5MB以内");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void uploadReceiptは他人の経費IDには紐付けできない() {
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "receipt.jpg", "image/jpeg", "dummy-image-content".getBytes());

        assertThatThrownBy(() -> expenseService.uploadReceipt(userA, 100, file))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(expenseRepository, never()).save(any());
    }
    
 // ===== F-08：月次集計表示（境界値） =====

    @Test
    void getSummaryは経費データが0件の月では合計0円で返す() {
        // 確定分・下書き分ともに空のリストが返る状況をMock化する
        when(expenseRepository.findSummaryByUserIdAndYearAndStatus(1, 2026, "registered"))
            .thenReturn(new ArrayList<>());
        when(expenseRepository.findSummaryByUserIdAndYearAndStatus(1, 2026, "draft"))
            .thenReturn(new ArrayList<>());

        SummaryResponseDto result = expenseService.getSummary(userA, 2026, 12);

        assertThat(result.getTotalAmount()).isEqualTo(0);
        assertThat(result.getCategoryBreakdown()).isEmpty();
        assertThat(result.getDraftAmount()).isEqualTo(0);
    }

    // --- F-21 消費税区分 / F-22 適格請求書チェック ---------------------------

    /**
     * 登録用のリクエストDTOを作る。消費税区分以外は固定値でよいため共通化する
     */
    private ExpenseRequestDto taxRequestDto(String taxCategory) {
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("消耗品");
        dto.setAmount(11000);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 30));
        dto.setTaxCategory(taxCategory);
        return dto;
    }

    /**
     * create()が成功する状況（自分のカテゴリが見つかる／saveは引数をそのまま返す）を作る
     */
    private void mockCreateSucceeds() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(categoryA));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * update()が成功する状況を作り、対象の既存経費を返す
     */
    private Expense mockUpdateSucceeds() {
        Expense existing = new Expense();
        existing.setId(100);
        existing.setUser(userA);
        existing.setCategory(categoryA);
        existing.setTaxCategory(Expense.TAX_CATEGORY_TAXABLE_10);

        // カテゴリは変更しない前提のため、カテゴリの再取得はスタブしない（F-28以降）
        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));
        return existing;
    }

    // F-21 No.1
    @Test
    void createは消費税区分を保存する() {
        mockCreateSucceeds();

        ExpenseResponseDto result =
            expenseService.create(userA, taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_8));

        assertThat(result.getTaxCategory()).isEqualTo("taxable_8");
    }

    // F-21 No.2
    @Test
    void createは区分を省略すると課税10パーセントになる() {
        mockCreateSucceeds();

        // DTOの既定値（taxable_10）のまま送られてくるケース
        ExpenseRequestDto dto = new ExpenseRequestDto();
        dto.setTitle("消耗品");
        dto.setAmount(11000);
        dto.setCategoryId(10);
        dto.setExpenseDate(LocalDate.of(2026, 7, 30));

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getTaxCategory()).isEqualTo("taxable_10");
    }

    // F-21 No.4
    @Test
    void updateは消費税区分を変更できる() {
        mockUpdateSucceeds();

        ExpenseResponseDto result =
            expenseService.update(userA, 100, taxRequestDto(Expense.TAX_CATEGORY_TAX_EXEMPT));

        assertThat(result.getTaxCategory()).isEqualTo("tax_exempt");
    }

    // F-21 No.5
    @Test
    void updateで課税区分以外に変更すると適格請求書の情報がnullになる() {
        Expense existing = mockUpdateSucceeds();
        existing.setIsQualifiedInvoice(true);
        existing.setVendorRegistrationNumber("T1234567890123");

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_NON_TAXABLE);
        dto.setIsQualifiedInvoice(true);
        dto.setVendorRegistrationNumber("T1234567890123");

        ExpenseResponseDto result = expenseService.update(userA, 100, dto);

        // 消費税の控除対象ではなくなるため、判定と根拠の番号をどちらも残さない
        assertThat(result.getIsQualifiedInvoice()).isNull();
        assertThat(result.getVendorRegistrationNumber()).isNull();
    }

    // F-22 No.1
    @Test
    void createは適格請求書フラグと登録番号を保存する() {
        mockCreateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10);
        dto.setIsQualifiedInvoice(true);
        dto.setVendorRegistrationNumber("T1234567890123");

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getIsQualifiedInvoice()).isTrue();
        assertThat(result.getVendorRegistrationNumber()).isEqualTo("T1234567890123");
    }

    // F-22 No.2
    @Test
    void createは登録番号を入力するとフラグが自動でtrueになる() {
        mockCreateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10);
        dto.setVendorRegistrationNumber("T1234567890123"); // フラグは未指定

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getIsQualifiedInvoice()).isTrue();
    }

    // F-22 No.3
    @Test
    void createはフラグを手動でfalseにできる() {
        mockCreateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10);
        dto.setVendorRegistrationNumber("T1234567890123");
        dto.setIsQualifiedInvoice(false); // 番号があっても手動の判断を尊重する

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getIsQualifiedInvoice()).isFalse();
        assertThat(result.getVendorRegistrationNumber()).isEqualTo("T1234567890123");
    }

    // F-22 No.4
    @Test
    void createは非課税の経費のフラグをnullにする() {
        mockCreateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAX_EXEMPT);
        dto.setIsQualifiedInvoice(true);

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getIsQualifiedInvoice()).isNull();
    }

    // F-22 No.5
    @Test
    void createは不課税の経費のフラグをnullにする() {
        mockCreateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_NON_TAXABLE);
        dto.setIsQualifiedInvoice(true);

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getIsQualifiedInvoice()).isNull();
    }

    // F-22 No.9
    @Test
    void updateで登録番号を後から追加するとフラグがtrueになる() {
        mockUpdateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10);
        dto.setVendorRegistrationNumber("T9999999999999");

        ExpenseResponseDto result = expenseService.update(userA, 100, dto);

        assertThat(result.getVendorRegistrationNumber()).isEqualTo("T9999999999999");
        assertThat(result.getIsQualifiedInvoice()).isTrue();
    }

    // --- F-28 無効化されたカテゴリの扱い -------------------------------------

    // 画面（S-02・S-03）ではプルダウンから除外しているが、
    // APIを直接呼ばれた場合にサーバー側でも弾けることを確認する
    @Test
    void createは無効化されたカテゴリを指定できない() {
        Category inactive = new Category();
        inactive.setId(10);
        inactive.setUser(userA);
        inactive.setName("旧カテゴリ");
        inactive.setIsActive(false);

        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() ->
                expenseService.create(userA, taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10)))
            .isInstanceOf(InactiveCategoryException.class)
            .hasMessageContaining("無効化されたカテゴリ");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateは同じカテゴリを選び直した場合は無効化済みでも通す() {
        // 編集中の経費の分類が意図せず変わるのを防ぐため（F-17の取引先と同じ方針）
        Category inactive = new Category();
        inactive.setId(10);
        inactive.setUser(userA);
        inactive.setName("旧カテゴリ");
        inactive.setIsActive(false);

        Expense existing = new Expense();
        existing.setId(100);
        existing.setUser(userA);
        existing.setCategory(inactive);
        existing.setTaxCategory(Expense.TAX_CATEGORY_TAXABLE_10);

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));

        ExpenseResponseDto result =
            expenseService.update(userA, 100, taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10));

        assertThat(result.getCategoryName()).isEqualTo("旧カテゴリ");
        // 同じカテゴリのため、有効かどうかの確認（＝カテゴリの再取得）は行わない
        verify(categoryRepository, never()).findByIdAndUserId(anyInt(), anyInt());
    }

    @Test
    void updateは別の無効化されたカテゴリには変更できない() {
        Category current = new Category();
        current.setId(10);
        current.setUser(userA);
        current.setName("交通費");
        current.setIsActive(true);

        Category otherInactive = new Category();
        otherInactive.setId(11);
        otherInactive.setUser(userA);
        otherInactive.setName("旧カテゴリ");
        otherInactive.setIsActive(false);

        Expense existing = new Expense();
        existing.setId(100);
        existing.setUser(userA);
        existing.setCategory(current);
        existing.setTaxCategory(Expense.TAX_CATEGORY_TAXABLE_10);

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndUserId(11, 1)).thenReturn(Optional.of(otherInactive));

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10);
        dto.setCategoryId(11);

        assertThatThrownBy(() -> expenseService.update(userA, 100, dto))
            .isInstanceOf(InactiveCategoryException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void 登録番号を空文字で送るとnullとして保存される() {
        mockCreateSucceeds();

        ExpenseRequestDto dto = taxRequestDto(Expense.TAX_CATEGORY_TAXABLE_10);
        dto.setVendorRegistrationNumber(""); // フォームで一度入力してから消した場合

        ExpenseResponseDto result = expenseService.create(userA, dto);

        assertThat(result.getVendorRegistrationNumber()).isNull();
        // 番号が無いのでフラグも自動trueにはしない
        assertThat(result.getIsQualifiedInvoice()).isNull();
    }
}