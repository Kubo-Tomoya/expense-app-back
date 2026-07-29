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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

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

    @Test
    void createは登録成功時に201とid_isActiveを返す() throws Exception {
        MockHttpSession session = registerAndLogin();

        String body = "{\"name\":\"株式会社サンプル商事\",\"honorific\":\"御中\"}";

        mockMvc.perform(post("/api/clients")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void deactivateは無効化成功時に200とisActive_falseを返す() throws Exception {
        MockHttpSession session = registerAndLogin();

        String createBody = "{\"name\":\"株式会社サンプル商事\",\"honorific\":\"御中\"}";
        MvcResult createResult = mockMvc.perform(post("/api/clients")
                .with(csrf())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

        Integer clientId = new ObjectMapper()
            .readTree(createResult.getResponse().getContentAsString())
            .get("id").asInt();

        mockMvc.perform(put("/api/clients/" + clientId + "/deactivate")
                .with(csrf())
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));
    }
}