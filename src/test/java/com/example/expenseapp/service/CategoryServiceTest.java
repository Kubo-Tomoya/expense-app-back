package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.expenseapp.dto.response.CategoryResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.repository.CategoryRepository;

/**
 * CategoryServiceのService層テスト。
 *
 * CategoryServiceはfindAll(User)の1メソッドのみを持つシンプルな構成のため、
 * 「ログイン中ユーザーのuser_idで正しく絞り込んだ結果を、
 * DTOへ正しく変換して返すか」のみを検証すれば十分と判断した。
 * user_idによる絞り込み自体（他人のデータが混ざらないこと）は
 * Repository層（CategoryRepositoryTest）の責務であり、ここでは
 * 「Repositoryが返した結果を、Serviceが正しく扱えているか」に焦点を絞る
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

    @Test
    void findAllはログイン中ユーザーのカテゴリ一覧をDTOに変換して返す() {
        Category category1 = new Category();
        category1.setId(10);
        category1.setUser(userA);
        category1.setName("交通費");
        category1.setDisplayOrder(0);
        category1.setCreatedAt(LocalDateTime.now());
        category1.setUpdatedAt(LocalDateTime.now());

        Category category2 = new Category();
        category2.setId(11);
        category2.setUser(userA);
        category2.setName("食費");
        category2.setDisplayOrder(1);
        category2.setCreatedAt(LocalDateTime.now());
        category2.setUpdatedAt(LocalDateTime.now());

        // 「userAのID(1)で検索したら、この2件が返ってくる」という状況をMock化。
        // user_idによる絞り込みの正しさ自体は、Repository層のテストで別途保証済み
        when(categoryRepository.findAllByUserIdOrderById(1))
            .thenReturn(List.of(category1, category2));

        List<CategoryResponseDto> result = categoryService.findAll(userA);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("交通費");
        assertThat(result.get(0).getDisplayOrder()).isEqualTo(0);
        assertThat(result.get(1).getName()).isEqualTo("食費");
    }

    @Test
    void findAllはカテゴリが0件の場合空のリストを返す() {
        // F-14検証時に確認した「カテゴリ0件のユーザー」の状態を再現するテスト。
        // 実際にPostmanで確認した内容を、自動テストとして定着させる意味を持つ
        when(categoryRepository.findAllByUserIdOrderById(1))
            .thenReturn(List.of());

        List<CategoryResponseDto> result = categoryService.findAll(userA);

        assertThat(result).isEmpty();
    }
}