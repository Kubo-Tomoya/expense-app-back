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

import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.User;

/**
 * ClientRepositoryのRepository層テスト。
 * ExpenseRepositoryTest・CategoryRepositoryTestと同じくTestcontainersを使用する
 */
@DataJpaTest
@Testcontainers
class ClientRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private ClientRepository clientRepository;

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

    @Test
    void findAllByUserIdOrderByNameは自分の取引先のみを会社名昇順で返す() {
        createClient(userA, "株式会社あいう商事");
        createClient(userA, "株式会社かきく物産");
        createClient(userB, "userBの取引先");

        List<Client> result = clientRepository.findAllByUserIdOrderByName(userA.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Client::getName)
            .containsExactly("株式会社あいう商事", "株式会社かきく物産");
    }

    @Test
    void findAllByUserIdOrderByNameは取引先0件のユーザーには空リストを返す() {
        List<Client> result = clientRepository.findAllByUserIdOrderByName(userB.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUserIdは自分の取引先を取得できる() {
        Client saved = createClient(userA, "株式会社サンプル商事");

        Optional<Client> result = clientRepository.findByIdAndUserId(saved.getId(), userA.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("株式会社サンプル商事");
    }

    @Test
    void findByIdAndUserIdは他人の取引先は取得できない() {
        Client saved = createClient(userA, "株式会社サンプル商事");

        Optional<Client> result = clientRepository.findByIdAndUserId(saved.getId(), userB.getId());

        assertThat(result).isEmpty();
    }
}