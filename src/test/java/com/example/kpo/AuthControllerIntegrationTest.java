package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.repository.AdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminRepository.deleteAll();
        Admin admin = new Admin();
        admin.setUsername("TEST");
        admin.setPassword(passwordEncoder.encode("password"));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("Успешный логин возвращает JWT токен")
    void loginReturnsJwtToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("TEST");
        request.setPassword("password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Защищенный эндпоинт без токена запрещен")
    void protectedEndpointWithoutTokenForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Защищенный эндпоинт с валидным токеном доступен")
    void protectedEndpointWithTokenAllowed() throws Exception {
        String token = obtainToken("TEST", "password");

        mockMvc.perform(get("/admin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Логаут с валидным токеном возвращает 200")
    void logoutWithTokenReturnsOk() throws Exception {
        String token = obtainToken("TEST", "password");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
