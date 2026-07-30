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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CategoryControllerのController層テスト（F-28）。
 * ExpenseControllerTestと同じ構成（本物のDB・Spring Security・MockMvc）を使う。
 *
 * 単体テスト仕様書F-28のNo.16に対応し、あわせてAPI経路での
 * 重複エラー・並び替え・無効化／再有効化を確認する
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ユーザー登録＋ログインを行いセッションを返す。
     * 登録時にデフォルトカテゴリ5件が自動作成される（F-14）
     */
    private MockHttpSession registerAndLogin() throws Exception {
        String body = "{\"email\":\"user-" + UUID.randomUUID()
            + "@example.com\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private JsonNode getCategories(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories").session(session))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // F-28 No.16
    @Test
    void 一覧のレスポンスに勘定科目と有効フラグと使用件数が含まれる() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/api/categories").session(session))
            .andExpect(status().isOk())
            // F-14で自動作成されるデフォルト5件
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].isActive").value(true))
            // 経費が無い状態では使用件数は0
            .andExpect(jsonPath("$[0].usageCount").value(0))
            // 未設定の勘定科目はnullで返る（DDLのUPDATE文はテスト用schema.sqlでは実行されない）
            .andExpect(jsonPath("$[0].taxFormCategory").doesNotExist());
    }

    @Test
    void createはカテゴリを作成し表示順を採番する() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(post("/api/categories")
                .with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"会議費\",\"taxFormCategory\":\"接待交際費\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("会議費"))
            .andExpect(jsonPath("$.taxFormCategory").value("接待交際費"))
            .andExpect(jsonPath("$.isActive").value(true))
            // F-14が自動作成するデフォルト5件は display_order が 0〜4 のため、
            // 既存の最大値（4）+1 で 5 が採番される。
            // 並び替え（PUT /api/categories/order）を行うと1から振り直される
            .andExpect(jsonPath("$.displayOrder").value(5));
    }

    @Test
    void createは同名のカテゴリを400で拒否する() throws Exception {
        MockHttpSession session = registerAndLogin();

        // デフォルトカテゴリと同じ「交通費」を登録しようとする
        mockMvc.perform(post("/api/categories")
                .with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"交通費\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("同じ名前のカテゴリ")));
    }

    @Test
    void createはカテゴリ名が空の場合400になる() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(post("/api/categories")
                .with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void updateOrderは表示順を一括更新する() throws Exception {
        MockHttpSession session = registerAndLogin();
        JsonNode categories = getCategories(session);

        // 先頭と末尾を入れ替えた配列を送る
        int firstId = categories.get(0).get("id").asInt();
        int lastId = categories.get(4).get("id").asInt();
        String ids = "[" + lastId + ","
            + categories.get(1).get("id").asInt() + ","
            + categories.get(2).get("id").asInt() + ","
            + categories.get(3).get("id").asInt() + ","
            + firstId + "]";

        mockMvc.perform(put("/api/categories/order")
                .with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":" + ids + "}"))
            .andExpect(status().isOk())
            // レスポンスは並び替え後の一覧。先頭が入れ替わったカテゴリになる
            .andExpect(jsonPath("$[0].id").value(lastId))
            .andExpect(jsonPath("$[0].displayOrder").value(1))
            .andExpect(jsonPath("$[4].id").value(firstId))
            .andExpect(jsonPath("$[4].displayOrder").value(5));
    }

    @Test
    void 無効化と再有効化ができる() throws Exception {
        MockHttpSession session = registerAndLogin();
        int categoryId = getCategories(session).get(0).get("id").asInt();

        mockMvc.perform(put("/api/categories/" + categoryId + "/deactivate")
                .with(csrf()).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));

        mockMvc.perform(put("/api/categories/" + categoryId + "/activate")
                .with(csrf()).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));

        // 再有効化は冪等（2回呼んでもエラーにならない）
        mockMvc.perform(put("/api/categories/" + categoryId + "/activate")
                .with(csrf()).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void 他ユーザーのカテゴリは更新できない() throws Exception {
        MockHttpSession sessionA = registerAndLogin();
        int categoryIdOfA = getCategories(sessionA).get(0).get("id").asInt();

        MockHttpSession sessionB = registerAndLogin();

        // userBのセッションでuserAのカテゴリIDを指定する
        mockMvc.perform(put("/api/categories/" + categoryIdOfA)
                .with(csrf()).session(sessionB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"乗っ取り\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void 未ログイン時は認証エラーになる() throws Exception {
        mockMvc.perform(post("/api/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"会議費\"}"))
            .andExpect(status().isUnauthorized());
    }
}
