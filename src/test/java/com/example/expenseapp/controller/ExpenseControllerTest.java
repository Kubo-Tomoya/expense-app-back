package com.example.expenseapp.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ExpenseControllerのController層テスト（F-01〜F-04の残り）。
 * AuthControllerTestと同じ構成（本物のDB・Spring Security・MockMvc）を使う
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /**
     * ユーザー登録＋ログインを行い、セッションを返す。
     * 登録時にデフォルトカテゴリ5件が自動作成されるため、
     * 経費登録に必要なcategoryIdもあわせて取得して返す
     */
    private TestUser registerAndLogin() throws Exception {
        String email = uniqueEmail();
        String registerBody = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
            .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // デフォルトカテゴリ「交通費」のIDを取得する
        MvcResult categoriesResult = mockMvc.perform(get("/api/categories").session(session))
            .andExpect(status().isOk())
            .andReturn();

        String categoriesJson = categoriesResult.getResponse().getContentAsString();
        // 先頭カテゴリ（交通費）のidを簡易的に抽出する
        Integer categoryId = new ObjectMapper().readTree(categoriesJson).get(0).get("id").asInt();

        return new TestUser(session, categoryId);
    }

    private record TestUser(MockHttpSession session, Integer categoryId) {}

    // ===== F-01：バリデーション・未ログイン =====

    @Test
    void createはバリデーションエラー時に400とerrors配列を返す() throws Exception {
        TestUser user = registerAndLogin();

        // タイトル空文字（不正な値）
        String body = "{\"title\":\"\",\"amount\":1000,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-10\"}";

        mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createは未ログイン時は401になる() throws Exception {
        String body = "{\"title\":\"新幹線代\",\"amount\":1000,\"categoryId\":1,\"expenseDate\":\"2026-07-10\"}";

        // セッションを一切渡さず、未ログイン状態でリクエストする
        mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    // ===== F-02：一覧取得のレスポンス形式 =====

    @Test
    void getAllは一覧取得時に200と配列形式で返す() throws Exception {
        TestUser user = registerAndLogin();

        mockMvc.perform(get("/api/expenses").session(user.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ===== F-03：存在しないIDへの更新 =====

    @Test
    void updateは存在しないIDだと404を返す() throws Exception {
        TestUser user = registerAndLogin();

        String body = "{\"title\":\"更新テスト\",\"amount\":1000,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-10\"}";

        // 存在しない経費ID(99999)を指定する
        mockMvc.perform(put("/api/expenses/99999")
                .with(csrf())
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }

    // ===== F-04：削除成功時のレスポンス =====

    @Test
    void deleteは削除成功時に204を返す() throws Exception {
        TestUser user = registerAndLogin();

        // 削除対象の経費をまず作成する
        String createBody = "{\"title\":\"削除テスト\",\"amount\":1000,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-10\"}";

        MvcResult createResult = mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

        Integer expenseId = new ObjectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .get("id").asInt();

        mockMvc.perform(delete("/api/expenses/" + expenseId)
                .with(csrf())
                .session(user.session()))
            .andExpect(status().isNoContent());
    }

    // ===== F-21 消費税区分 / F-22 適格請求書チェック =====

    // F-21 No.11／F-22 No.10
    @Test
    void createとgetのレスポンスに消費税区分と適格請求書の情報が含まれる() throws Exception {
        TestUser user = registerAndLogin();

        String body = "{\"title\":\"書籍代\",\"amount\":10800,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-30\",\"taxCategory\":\"taxable_8\""
            + ",\"vendorRegistrationNumber\":\"T1234567890123\"}";

        MvcResult createResult = mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.taxCategory").value("taxable_8"))
            // 登録番号を入力しているため、フラグは自動でtrueになる
            .andExpect(jsonPath("$.isQualifiedInvoice").value(true))
            .andExpect(jsonPath("$.vendorRegistrationNumber").value("T1234567890123"))
            .andReturn();

        Integer expenseId = new ObjectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .get("id").asInt();

        mockMvc.perform(get("/api/expenses/" + expenseId).session(user.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taxCategory").value("taxable_8"))
            .andExpect(jsonPath("$.isQualifiedInvoice").value(true))
            .andExpect(jsonPath("$.vendorRegistrationNumber").value("T1234567890123"));
    }

    @Test
    void createは消費税区分を省略すると課税10パーセントで登録される() throws Exception {
        TestUser user = registerAndLogin();

        String body = "{\"title\":\"消耗品\",\"amount\":1100,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-30\"}";

        mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.taxCategory").value("taxable_10"))
            // 登録番号もフラグも指定していないためnullのまま
            .andExpect(jsonPath("$.isQualifiedInvoice").doesNotExist());
    }

    @Test
    void createは定義外の消費税区分を400で拒否する() throws Exception {
        TestUser user = registerAndLogin();

        String body = "{\"title\":\"消耗品\",\"amount\":1100,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-30\",\"taxCategory\":\"taxable_5\"}";

        mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    // F-21 No.12
    @Test
    void getSummaryの合計金額は消費税区分を追加しても税込のまま変わらない() throws Exception {
        TestUser user = registerAndLogin();

        // 課税10%（税込1,100円）と非課税（税込1,000円）を1件ずつ登録する
        String taxable = "{\"title\":\"消耗品\",\"amount\":1100,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-15\",\"taxCategory\":\"taxable_10\"}";
        String exempt = "{\"title\":\"家賃\",\"amount\":1000,\"categoryId\":" + user.categoryId()
            + ",\"expenseDate\":\"2026-07-16\",\"taxCategory\":\"tax_exempt\"}";

        for (String body : new String[] { taxable, exempt }) {
            mockMvc.perform(post("/api/expenses")
                    .with(csrf())
                    .session(user.session())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isCreated());
        }

        // F-20で定めた「集計金額は税込」の原則が変わっていないことの確認。
        // 税抜に変換されていれば2,100円にはならない
        mockMvc.perform(get("/api/expenses/summary")
                .session(user.session())
                .param("year", "2026")
                .param("month", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(2100));
    }
}