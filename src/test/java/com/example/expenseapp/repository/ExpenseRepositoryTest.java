package com.example.expenseapp.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
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
import com.example.expenseapp.entity.Expense;
import com.example.expenseapp.entity.User;

/**
 * ExpenseRepositoryのRepository層テスト。
 *
 * @DataJpaTestは、JPA関連のBean（Repository・Entity）のみを読み込む
 * 軽量なテストの仕組み。@ServiceConnectionにより、本物のPostgreSQL
 * （Testcontainersが自動起動するDockerコンテナ）に接続してテストする。
 * これにより、TO_CHAR等のPostgreSQL固有関数を含むクエリも、
 * 本番同様の挙動でテストできる
 */
@DataJpaTest
@Testcontainers
class ExpenseRepositoryTest {

    // テスト全体で1つのコンテナを使い回す（@Containerは各テストクラスで
    // 個別にコンテナを起動するため、実行時間短縮のためstaticにしている）
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;
    private Category categoryA;

    /**
     * 各テストの実行前に、共通の前提データ（ユーザー2人、カテゴリ1件）を準備する。
     * userA・userBという2ユーザーを用意することで、
     * 「自分のデータは見える」「他人のデータは見えない」の両方を確認できるようにする
     */
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

        categoryA = new Category();
        categoryA.setUser(userA);
        categoryA.setName("交通費");
        categoryA.setDisplayOrder(0);
        categoryA.setCreatedAt(LocalDateTime.now());
        categoryA.setUpdatedAt(LocalDateTime.now());
        categoryA = categoryRepository.save(categoryA);
    }

    private Expense createExpense(User user, Category category, String title, LocalDate date) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setCategory(category);
        expense.setTitle(title);
        expense.setAmount(1000);
        expense.setExpenseDate(date);
        expense.setStatus("registered");
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        return expenseRepository.save(expense);
    }

    @Test
    void 自分の経費はfindByIdAndUserIdで取得できる() {
        Expense saved = createExpense(userA, categoryA, "新幹線代", LocalDate.of(2026, 7, 10));

        Optional<Expense> result = expenseRepository.findByIdAndUserId(saved.getId(), userA.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("新幹線代");
    }

    @Test
    void 他人の経費はfindByIdAndUserIdで取得できない() {
        // userAが所有する経費を作成
        Expense saved = createExpense(userA, categoryA, "新幹線代", LocalDate.of(2026, 7, 10));

        // userBのIDで同じ経費IDを検索する（F-14の核心：他人IDでの検索は失敗すべき）
        Optional<Expense> result = expenseRepository.findByIdAndUserId(saved.getId(), userB.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByUserIdOrderByExpenseDateDescは自分の経費のみを返す() {
        createExpense(userA, categoryA, "userAの経費1", LocalDate.of(2026, 7, 1));
        createExpense(userA, categoryA, "userAの経費2", LocalDate.of(2026, 7, 15));
        // userBのカテゴリを別途作成し、userBの経費も1件作っておく
        Category categoryB = new Category();
        categoryB.setUser(userB);
        categoryB.setName("食費");
        categoryB.setDisplayOrder(0);
        categoryB.setCreatedAt(LocalDateTime.now());
        categoryB.setUpdatedAt(LocalDateTime.now());
        categoryB = categoryRepository.save(categoryB);
        createExpense(userB, categoryB, "userBの経費", LocalDate.of(2026, 7, 10));

        List<Expense> result = expenseRepository.findAllByUserIdOrderByExpenseDateDesc(userA.getId());

        // userAの経費2件のみが返り、userBの経費が混ざっていないことを確認
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Expense::getTitle)
            .containsExactlyInAnyOrder("userAの経費1", "userAの経費2");
    }

    @Test
    void findAllByUserIdOrderByExpenseDateDescは発生日の降順で返す() {
        createExpense(userA, categoryA, "古い経費", LocalDate.of(2026, 7, 1));
        createExpense(userA, categoryA, "新しい経費", LocalDate.of(2026, 7, 20));

        List<Expense> result = expenseRepository.findAllByUserIdOrderByExpenseDateDesc(userA.getId());

        // メソッド名の通り「降順」であることを確認（1件目が新しい方であるべき）
        assertThat(result.get(0).getTitle()).isEqualTo("新しい経費");
        assertThat(result.get(1).getTitle()).isEqualTo("古い経費");
    }

    @Test
    void findByUserIdAndMonthはTO_CHARによる月指定検索が正しく機能する() {
        // 過去にDATE_FORMAT(MySQL構文)がPostgreSQLで動かず修正した経緯があるため、
        // 本物のPostgreSQL(Testcontainers)でTO_CHARが正しく機能するかを確認する
        createExpense(userA, categoryA, "7月の経費", LocalDate.of(2026, 7, 15));
        createExpense(userA, categoryA, "8月の経費", LocalDate.of(2026, 8, 1));

        List<Expense> result = expenseRepository.findByUserIdAndMonth(userA.getId(), "2026-07");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("7月の経費");
    }

    @Test
    void findByUserIdAndMonthは他人の経費を含めない() {
        createExpense(userA, categoryA, "userAの7月経費", LocalDate.of(2026, 7, 15));
        Category categoryB = new Category();
        categoryB.setUser(userB);
        categoryB.setName("食費");
        categoryB.setDisplayOrder(0);
        categoryB.setCreatedAt(LocalDateTime.now());
        categoryB.setUpdatedAt(LocalDateTime.now());
        categoryB = categoryRepository.save(categoryB);
        createExpense(userB, categoryB, "userBの7月経費", LocalDate.of(2026, 7, 20));

        List<Expense> result = expenseRepository.findByUserIdAndMonth(userA.getId(), "2026-07");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("userAの7月経費");
    }
}