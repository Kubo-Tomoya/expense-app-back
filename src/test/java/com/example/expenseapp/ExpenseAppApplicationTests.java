package com.example.expenseapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * アプリケーション全体のコンテキストが起動できることの確認。
 *
 * テスト用のapplication.ymlは本番のapplication.ymlをクラスパス上で置き換えるため、
 * このテストだけではDB接続先が決まらず「Failed to determine a suitable driver class」で
 * 失敗していた。InvoicePdfServiceTestと同じくTestcontainersConfigを取り込み、
 * @ServiceConnectionが接続情報を注入する構成に揃えている。
 *
 * 実行にはDockerが必要（Repository層のテストと同じ前提）
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ExpenseAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
