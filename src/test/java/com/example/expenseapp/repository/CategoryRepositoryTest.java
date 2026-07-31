package com.example.expenseapp.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.User;

/**
 * CategoryRepositoryのRepository層テスト。
 * ExpenseRepositoryTestと同じくTestcontainers（本物のPostgreSQL）を使用する
 */
@DataJpaTest
@Testcontainers
class CategoryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setEmail("userA@example.com");
        userA.setPasswordHash("dummy-hash");
        userA.setCreatedAt(LocalDateTime.now());
        userA.setUpdatedAt(LocalDateTime.now());
        userA = userRepository.save(userA);

        userB = new User();
        userB.setEmail("userB@example.com");
        userB.setPasswordHash("dummy-hash");
        userB.setCreatedAt(LocalDateTime.now());
        userB.setUpdatedAt(LocalDateTime.now());
        userB = userRepository.save(userB);
    }

    private Category createCategory(User user, String name, int displayOrder) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Test
    void findAllByUserIdOrderByIdは自分のカテゴリのみをid昇順で返す() {
        createCategory(userA, "交通費", 0);
        createCategory(userA, "食費", 1);
        createCategory(userB, "userBのカテゴリ", 0);

        List<Category> result = categoryRepository.findAllByUserIdOrderById(userA.getId());

        // userAの2件のみが返り、userBのカテゴリが混ざらないことを確認
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Category::getName)
            .containsExactly("交通費", "食費"); // id昇順（作成順）で並んでいることも確認
    }

    @Test
    void findAllByUserIdOrderByIdはカテゴリが0件のユーザーには空リストを返す() {
        // F-14検証時に実際に確認した「カテゴリ0件のユーザー」の状態をここでも再現する
        List<Category> result = categoryRepository.findAllByUserIdOrderById(userB.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUserIdは自分のカテゴリを取得できる() {
        Category saved = createCategory(userA, "交通費", 0);

        Optional<Category> result = categoryRepository.findByIdAndUserId(saved.getId(), userA.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("交通費");
    }

    @Test
    void findByIdAndUserIdは他人のカテゴリを取得できない() {
        Category saved = createCategory(userA, "交通費", 0);

        Optional<Category> result = categoryRepository.findByIdAndUserId(saved.getId(), userB.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void 同一ユーザー内では同名カテゴリを作成できない() {
        createCategory(userA, "交通費", 0);

        // UNIQUE制約(user_id, name)違反により例外が発生することを確認
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                createCategory(userA, "交通費", 1);
                categoryRepository.flush(); // 制約違反はSQL発行時に検知されるため、flushで即座に実行させる
            }
        );
    }

    @Test
    void 異なるユーザー間では同名カテゴリが共存できる() {
        createCategory(userA, "交通費", 0);

        // userAとuserBが同じ「交通費」という名前を使っても、
        // UNIQUE制約はuser_id+nameの組み合わせなので問題なく共存できることを確認
        Category categoryB = createCategory(userB, "交通費", 0);

        assertThat(categoryB.getId()).isNotNull();
    }

    // --- F-28 カテゴリ管理 ---------------------------------------------------

    // F-28 No.1
    @Test
    void findAllByUserIdOrderByDisplayOrderAscは自分のカテゴリのみを表示順で返す() {
        // 登録順（id順）と表示順が一致しない状態を作る
        createCategory(userA, "通信費", 3);
        createCategory(userA, "交通費", 1);
        createCategory(userA, "食費", 2);
        createCategory(userB, "userBのカテゴリ", 1);

        List<Category> result = categoryRepository.findAllByUserIdOrderByDisplayOrderAsc(userA.getId());

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Category::getName)
            .containsExactly("交通費", "食費", "通信費");
    }

    @Test
    void findByUserIdAndNameは同一ユーザー内の同名カテゴリを見つける() {
        createCategory(userA, "交通費", 1);
        createCategory(userB, "交通費", 1);

        // userAで検索するとuserAのカテゴリだけが返る（重複判定を自分の中だけで行うため）
        Optional<Category> found = categoryRepository.findByUserIdAndName(userA.getId(), "交通費");
        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(userA.getId());

        assertThat(categoryRepository.findByUserIdAndName(userA.getId(), "存在しない名前")).isEmpty();
    }

    @Test
    void findMaxDisplayOrderByUserIdは表示順の最大値を返しカテゴリ0件では0を返す() {
        // 1件も無い状態ではCOALESCEにより0が返る（新規登録時に1が採番される）
        assertThat(categoryRepository.findMaxDisplayOrderByUserId(userA.getId())).isZero();

        createCategory(userA, "交通費", 1);
        createCategory(userA, "食費", 5);
        createCategory(userB, "userBのカテゴリ", 99); // 他人の値は含めない

        assertThat(categoryRepository.findMaxDisplayOrderByUserId(userA.getId())).isEqualTo(5);
    }

    @Test
    void is_activeは既定でtrueになる() {
        Category category = createCategory(userA, "交通費", 1);

        // DDLのDEFAULT true とエンティティの初期値が揃っていることの確認。
        // 既存データの移行作業を不要にするための前提
        assertThat(category.getIsActive()).isTrue();
    }

    @Test
    void tax_form_categoryは未設定ならnullで保存される() {
        Category category = createCategory(userA, "交通費", 1);

        // 未設定はF-23の集計で「雑費」として扱うため、空文字ではなくnullで保持する
        assertThat(category.getTaxFormCategory()).isNull();
    }
}