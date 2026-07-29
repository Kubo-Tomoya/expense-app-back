package com.example.expenseapp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テスト実行時、本番と同じPostgreSQLのDockerコンテナを自動起動するための設定。
 * @ServiceConnectionにより、Spring Bootが自動的にこのコンテナへの
 * 接続情報（application.yml相当）を認識してくれるため、
 * 手動でテスト用application.ymlを別途用意する必要がない
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}