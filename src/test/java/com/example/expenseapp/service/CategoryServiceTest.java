package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.expenseapp.dto.request.CategoryOrderRequestDto;
import com.example.expenseapp.dto.request.CategoryRequestDto;
import com.example.expenseapp.dto.response.CategoryResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.DuplicateCategoryException;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.CategoryRepository;

/**
 * CategoryServiceのService層テスト。
 *
 * F-28でカテゴリの登録・更新・並び替え・無効化／再有効化を追加したため、
 * user_idによる絞り込み自体（Repository層の責務）ではなく、
 * 「Repositoryが返した結果をServiceが正しく扱えているか」に焦点を絞る。
 *
 * 単体テスト仕様書F-28のNo.3〜15に対応する（No.1・2はCategoryRepositoryTest）
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private User userA;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1);
        userA.setEmail("userA@example.com");
    }

    private Category category(Integer id, String name, int displayOrder) {
        Category category = new Category();
        category.setId(id);
        category.setUser(userA);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setIsActive(true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return category;
    }

    private CategoryRequestDto requestDto(String name, String taxFormCategory) {
        CategoryRequestDto dto = new CategoryRequestDto();
        dto.setName(name);
        dto.setTaxFormCategory(taxFormCategory);
        return dto;
    }

    /** saveは引数をそのまま返す（IDの採番自体は検証対象外のため） */
    private void mockSaveReturnsArgument() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void findAllはログイン中ユーザーのカテゴリ一覧をDTOに変換して返す() {
        // 表示順（display_order）で取得することを確認する。
        // 旧来のfindAllByUserIdOrderByIdは登録順であり、S-15の並び替えを反映できない
        when(categoryRepository.findAllByUserIdOrderByDisplayOrderAsc(1))
            .thenReturn(List.of(category(10, "交通費", 1), category(11, "食費", 2)));
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());

        List<CategoryResponseDto> result = categoryService.findAll(userA);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("交通費");
        assertThat(result.get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(result.get(1).getName()).isEqualTo("食費");
    }

    @Test
    void findAllはカテゴリが0件の場合空のリストを返す() {
        when(categoryRepository.findAllByUserIdOrderByDisplayOrderAsc(1)).thenReturn(List.of());
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());

        assertThat(categoryService.findAll(userA)).isEmpty();
    }

    @Test
    void findAllは使用件数を返し経費が無いカテゴリは0になる() {
        when(categoryRepository.findAllByUserIdOrderByDisplayOrderAsc(1))
            .thenReturn(List.of(category(10, "交通費", 1), category(11, "食費", 2)));
        // 交通費（id=10）のみ3件使われている状態
        when(categoryRepository.countExpensesByCategory(1))
            .thenReturn(List.<Object[]>of(new Object[] { 10, 3L }));

        List<CategoryResponseDto> result = categoryService.findAll(userA);

        assertThat(result.get(0).getUsageCount()).isEqualTo(3L);
        // 行が返らないカテゴリは0を補う
        assertThat(result.get(1).getUsageCount()).isZero();
    }

    // F-28 No.3
    @Test
    void createは新規カテゴリを有効な状態で作成する() {
        when(categoryRepository.findByUserIdAndName(1, "会議費")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxDisplayOrderByUserId(1)).thenReturn(5);
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.create(userA, requestDto("会議費", "接待交際費"));

        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getName()).isEqualTo("会議費");
        assertThat(result.getTaxFormCategory()).isEqualTo("接待交際費");
    }

    // F-28 No.4
    @Test
    void createは表示順を既存の最大値プラス1で採番する() {
        when(categoryRepository.findByUserIdAndName(1, "会議費")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxDisplayOrderByUserId(1)).thenReturn(5);
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.create(userA, requestDto("会議費", null));

        assertThat(result.getDisplayOrder()).isEqualTo(6);
    }

    // F-28 No.5
    @Test
    void createは同一ユーザー内で同名のカテゴリを登録できない() {
        when(categoryRepository.findByUserIdAndName(1, "交通費"))
            .thenReturn(Optional.of(category(10, "交通費", 1)));

        assertThatThrownBy(() -> categoryService.create(userA, requestDto("交通費", null)))
            .isInstanceOf(DuplicateCategoryException.class)
            .hasMessageContaining("交通費");

        verify(categoryRepository, never()).save(any());
    }

    // F-28 No.6
    @Test
    void createは他ユーザーと同名のカテゴリを登録できる() {
        // 他ユーザーが「交通費」を持っていても、自分のuser_idでは見つからない状況を模擬する。
        // 一意制約が user_id + name の複合であるため登録できる（F-14で決定）
        when(categoryRepository.findByUserIdAndName(1, "交通費")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxDisplayOrderByUserId(1)).thenReturn(0);
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.create(userA, requestDto("交通費", null));

        assertThat(result.getName()).isEqualTo("交通費");
        assertThat(result.getDisplayOrder()).isEqualTo(1);
    }

    // F-28 No.7
    @Test
    void createは勘定科目が未設定でも登録できる() {
        when(categoryRepository.findByUserIdAndName(1, "雑費対象")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxDisplayOrderByUserId(1)).thenReturn(0);
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.create(userA, requestDto("雑費対象", null));

        assertThat(result.getTaxFormCategory()).isNull();
    }

    @Test
    void createはカテゴリ名の前後の空白を除去する() {
        when(categoryRepository.findByUserIdAndName(1, "会議費")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxDisplayOrderByUserId(1)).thenReturn(0);
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.create(userA, requestDto("  会議費  ", "  接待交際費  "));

        assertThat(result.getName()).isEqualTo("会議費");
        assertThat(result.getTaxFormCategory()).isEqualTo("接待交際費");
    }

    @Test
    void createは勘定科目が空文字ならnullとして保存する() {
        when(categoryRepository.findByUserIdAndName(1, "会議費")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxDisplayOrderByUserId(1)).thenReturn(0);
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.create(userA, requestDto("会議費", ""));

        assertThat(result.getTaxFormCategory()).isNull();
    }

    // F-28 No.8
    @Test
    void updateはカテゴリ名と勘定科目を更新できる() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(category(10, "交通費", 1)));
        when(categoryRepository.findByUserIdAndName(1, "旅費")).thenReturn(Optional.empty());
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.update(userA, 10, requestDto("旅費", "旅費交通費"));

        assertThat(result.getName()).isEqualTo("旅費");
        assertThat(result.getTaxFormCategory()).isEqualTo("旅費交通費");
    }

    @Test
    void updateは名前を変えずに勘定科目だけ変更できる() {
        // 自分自身との名前の衝突を重複と判定しないことの確認
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(category(10, "交通費", 1)));
        when(categoryRepository.findByUserIdAndName(1, "交通費"))
            .thenReturn(Optional.of(category(10, "交通費", 1)));
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.update(userA, 10, requestDto("交通費", "旅費交通費"));

        assertThat(result.getTaxFormCategory()).isEqualTo("旅費交通費");
    }

    @Test
    void updateは別のカテゴリと同名にはできない() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(category(10, "交通費", 1)));
        when(categoryRepository.findByUserIdAndName(1, "食費"))
            .thenReturn(Optional.of(category(11, "食費", 2)));

        assertThatThrownBy(() -> categoryService.update(userA, 10, requestDto("食費", null)))
            .isInstanceOf(DuplicateCategoryException.class);

        verify(categoryRepository, never()).save(any());
    }

    // F-28 No.9
    @Test
    void updateは他人のカテゴリを更新できない() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(userA, 10, requestDto("旅費", null)))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(categoryRepository, never()).save(any());
    }

    // F-28 No.10
    @Test
    void deactivateはis_activeがfalseに更新されレコードは削除されない() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(category(10, "交通費", 1)));
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.deactivate(userA, 10);

        assertThat(result.getIsActive()).isFalse();
        // 過去の経費から参照され続けるため、削除メソッドは一切呼ばれない
        verify(categoryRepository, never()).delete(any());
        verify(categoryRepository, never()).deleteById(any());
    }

    // F-28 No.11
    @Test
    void deactivateは使用中のカテゴリでも無効化できる() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(category(10, "交通費", 1)));
        // このカテゴリを使う経費が5件ある状態
        when(categoryRepository.countExpensesByCategory(1))
            .thenReturn(List.<Object[]>of(new Object[] { 10, 5L }));
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.deactivate(userA, 10);

        assertThat(result.getIsActive()).isFalse();
        // 使用件数はそのまま返る（経費側のcategory_idは変更しない）
        assertThat(result.getUsageCount()).isEqualTo(5L);
    }

    // F-28 No.12
    @Test
    void activateはis_activeがtrueに戻る() {
        Category inactive = category(10, "交通費", 1);
        inactive.setIsActive(false);
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(inactive));
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.activate(userA, 10);

        assertThat(result.getIsActive()).isTrue();
    }

    // F-28 No.13
    @Test
    void activateは既に有効なカテゴリでも例外にならない() {
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(category(10, "交通費", 1)));
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());
        mockSaveReturnsArgument();

        CategoryResponseDto result = categoryService.activate(userA, 10);

        assertThat(result.getIsActive()).isTrue();
    }

    // F-28 No.14
    @Test
    void updateOrderは表示順を1から振り直す() {
        Category first = category(10, "交通費", 1);
        Category second = category(11, "食費", 2);
        Category third = category(12, "通信費", 3);

        when(categoryRepository.findByIdAndUserId(12, 1)).thenReturn(Optional.of(third));
        when(categoryRepository.findByIdAndUserId(10, 1)).thenReturn(Optional.of(first));
        when(categoryRepository.findByIdAndUserId(11, 1)).thenReturn(Optional.of(second));
        when(categoryRepository.findAllByUserIdOrderByDisplayOrderAsc(1))
            .thenReturn(List.of(third, first, second));
        when(categoryRepository.countExpensesByCategory(1)).thenReturn(List.of());
        mockSaveReturnsArgument();

        CategoryOrderRequestDto dto = new CategoryOrderRequestDto();
        dto.setCategoryIds(List.of(12, 10, 11)); // 通信費を先頭へ

        categoryService.updateOrder(userA, dto);

        assertThat(third.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
        assertThat(second.getDisplayOrder()).isEqualTo(3);
    }

    // F-28 No.15
    @Test
    void updateOrderは他人のカテゴリIDが混ざっていると例外になる() {
        when(categoryRepository.findByIdAndUserId(10, 1))
            .thenReturn(Optional.of(category(10, "交通費", 1)));
        // 99は他人のカテゴリ（自分のuser_idでは見つからない）
        when(categoryRepository.findByIdAndUserId(99, 1)).thenReturn(Optional.empty());

        CategoryOrderRequestDto dto = new CategoryOrderRequestDto();
        dto.setCategoryIds(List.of(10, 99));

        assertThatThrownBy(() -> categoryService.updateOrder(userA, dto))
            .isInstanceOf(ResourceNotFoundException.class);

        // @Transactionalによりロールバックされるため、部分的に並び順が変わることはない。
        // ここでは一覧の再取得（正常終了時のみ行う処理）に到達していないことを確認する
        verify(categoryRepository, never()).findAllByUserIdOrderByDisplayOrderAsc(anyInt());
    }
}
