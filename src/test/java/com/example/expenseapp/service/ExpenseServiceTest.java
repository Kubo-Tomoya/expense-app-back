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

        when(expenseRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(expense));
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(categoryA));
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
}