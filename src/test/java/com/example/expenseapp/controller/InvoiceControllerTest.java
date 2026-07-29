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
 * 請求書APIのController層テスト（F-17）。
 * ClientControllerTestと同じく、登録→ログインでセッションを取得してから各APIを叩く
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpSession registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    /** 請求書の宛先に使う取引先を1件作成し、そのIDを返す */
    private Integer createClient(MockHttpSession session) throws Exception {
        String body = "{\"name\":\"株式会社サンプル商事\",\"honorific\":\"御中\"}";
        MvcResult result = mockMvc.perform(post("/api/clients")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    private String invoiceBody(Integer clientId) {
        return "{"
            + "\"clientId\":" + clientId + ","
            + "\"issueDate\":\"2026-07-27\","
            + "\"items\":[{"
            + "\"description\":\"Webサイト制作\","
            + "\"quantity\":1,"
            + "\"unitPrice\":100000,"
            + "\"taxCategory\":\"taxable_10\""
            + "}]}";
    }

    // No.27
    @Test
    void createは作成成功時に201と下書き状態を返す() throws Exception {
        MockHttpSession session = registerAndLogin();
        Integer clientId = createClient(session);

        mockMvc.perform(post("/api/invoices")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(clientId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.invoiceNumber").doesNotExist())
            .andExpect(jsonPath("$.subtotalAmount").value(100000))
            .andExpect(jsonPath("$.taxAmount").value(10000))
            .andExpect(jsonPath("$.totalAmount").value(110000))
            // 支払期日は未指定のため、発行日の翌月末が設定される
            .andExpect(jsonPath("$.dueDate").value("2026-08-31"));
    }

    // No.28
    @Test
    void issueは発行成功時に200と請求書番号を返す() throws Exception {
        MockHttpSession session = registerAndLogin();
        Integer clientId = createClient(session);

        MvcResult createResult = mockMvc.perform(post("/api/invoices")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(clientId)))
            .andExpect(status().isCreated())
            .andReturn();

        Integer invoiceId = objectMapper
            .readTree(createResult.getResponse().getContentAsString())
            .get("id").asInt();

        mockMvc.perform(put("/api/invoices/" + invoiceId + "/issue")
                .with(csrf())
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("issued"))
            .andExpect(jsonPath("$.invoiceNumber").value("INV-2026-0001"))
            .andExpect(jsonPath("$.clientName").value("株式会社サンプル商事"));
    }

    // No.29
    @Test
    void 未ログイン時は認証エラーになる() throws Exception {
        mockMvc.perform(get("/api/invoices"))
            .andExpect(status().isUnauthorized());
    }
}
