package com.example.expenseapp.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.expenseapp.TestcontainersConfig;
import com.example.expenseapp.service.MailService;

/**
 * AuthControllerのController層テスト（F-12：No.6〜14）。
 *
 * これまでのService層テスト（Mockito）とは異なり、本物のSpring Security・
 * 本物のDB(Testcontainers)を使う。理由：ログイン処理自体が
 * 「DB検索→パスワード照合→セッション発行」という一連の流れであり、
 * 一部だけモック化すると本来検証したい流れ自体が壊れてしまうため。
 *
 * .with(csrf())について：
 * SecurityConfigで再有効化したCSRF保護により、POSTリクエストには
 * 本来CSRFトークンが必要になる。csrf()はテスト用に「有効なCSRFトークンが
 * 付与された」状態を模擬するpost-processor（spring-security-test提供）で、
 * CSRFの仕組み自体をテストするわけではなく、業務ロジックの検証に集中するために使う
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

 // MailServiceを本物ではなく「偽物」に差し替える。
    // これにより、本物のJavaMailSender(Gmail SMTP接続)が一切不要になり、
    // メール送信を伴わない登録・ログインのテストに専念できる。
    // @MockBeanは、Spring起動時にこのBean自体をMockitoのモックに
    // 置き換える仕組みのため、MailServiceが依存するJavaMailSenderの
    // 解決自体が発生しなくなる（依存関係の連鎖が断ち切られる）
    @MockitoBean
    private MailService mailService;
    // 各テストで一意なメールアドレスを使うためのヘルパー。
    // 全テストが同じDB(Testcontainersコンテナ)を共有するため、
    // 固定のメールアドレスを使うとテスト間でデータが衝突してしまう
    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void 登録成功時は201とid_emailを返す() throws Exception {
        String email = uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void 重複登録時は409を返す() throws Exception {
        String email = uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        // 1回目：正常に登録される
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        // 2回目：同じメールアドレスで登録しようとすると409になる
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("このメールアドレスは既に登録されています"));
    }

    @Test
    void バリデーションエラー時は400とerrors配列を返す() throws Exception {
        // 空のメールアドレス・7文字のパスワード（両方とも不正な値）
        String body = "{\"email\":\"\",\"password\":\"1234567\"}";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void ログイン成功時は200とセッションCookieを返す() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123");

        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            // セッションが実際に生成されたことの確認（Set-Cookieの代わりに、
            // MockMvc上ではHttpSessionが作られたかどうかで検証する）
            .andExpect(result -> {
                HttpSession session = result.getRequest().getSession(false);
                org.assertj.core.api.Assertions.assertThat(session).isNotNull();
            });
    }

    @Test
    void パスワード誤りの場合は401とメッセージを返す() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123");

        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
    }

    @Test
    void 存在しないメールアドレスの場合もパスワード誤りと同じ401メッセージを返す() throws Exception {
        // メールエニュメレーション対策：存在有無で応答を変えず、
        // 前のテスト(パスワード誤り)と全く同じ文言を返すことを確認する
        String loginBody = "{\"email\":\"" + uniqueEmail() + "\",\"password\":\"anything\"}";

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message", equalTo("メールアドレスまたはパスワードが正しくありません")));
    }

    @Test
    void ログイン中はmeで200とユーザー情報を返す() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123");
        MockHttpSession session = loginAndGetSession(email, "password123");

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void 未ログイン時のmeは401になる_リグレッション確認() throws Exception {
        // 過去にClassCastExceptionで500になっていたバグの再発防止テスト。
        // セッションを一切渡さず、未ログイン(匿名ユーザー)状態でアクセスする
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ログアウト後はセッションが破棄されmeが401になる() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123");
        MockHttpSession session = loginAndGetSession(email, "password123");

        mockMvc.perform(post("/api/auth/logout")
                .with(csrf())
                .session(session))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    // ---- テスト用ヘルパーメソッド ----

    private void registerUser(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    /**
     * ログインを実行し、以降のリクエストで使い回せるHttpSessionを返す。
     * MockMvcでは、同じHttpSessionオブジェクトを次のリクエストの.session()に
     * 渡すことで、ブラウザがCookieを自動送信するのと同じ「ログイン状態の継続」を再現できる
     */
    private MockHttpSession loginAndGetSession(String email, String password) throws Exception {
        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}