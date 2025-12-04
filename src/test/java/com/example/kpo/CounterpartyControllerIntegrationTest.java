package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Counterparty;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CounterpartyRepository;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CounterpartyControllerIntegrationTest {

    private static final String USERNAME = "COUNTERPARTY_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        counterpartyRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("GET /counterparties возвращает всех контрагентов")
    void getAllCounterpartiesReturnsData() throws Exception {
        Counterparty first = counterpartyRepository.save(new Counterparty(null, "ООО Ромашка", "+79000000001", "info1"));
        Counterparty second = counterpartyRepository.save(new Counterparty(null, "ООО Липа", "+79000000002", null));

        mockMvc.perform(get("/counterparties")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.getId().intValue(),
                        second.getId().intValue()
                )));
    }

    @Test
    @DisplayName("GET /counterparties/{id} возвращает контрагента по id")
    void getCounterpartyByIdReturnsEntity() throws Exception {
        Counterparty counterparty = counterpartyRepository.save(new Counterparty(null, "ООО Пример", "+79001234567", "notes"));

        mockMvc.perform(get("/counterparties/{id}", counterparty.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(counterparty.getId().intValue())))
                .andExpect(jsonPath("$.name", is("ООО Пример")))
                .andExpect(jsonPath("$.phone", is("+79001234567")))
                .andExpect(jsonPath("$.info", is("notes")));
    }

    @Test
    @DisplayName("POST /counterparties создаёт контрагента")
    void createCounterpartyReturnsCreated() throws Exception {
        Counterparty payload = new Counterparty(null, "ООО Новый", "+79001112233", null);

        mockMvc.perform(post("/counterparties")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("ООО Новый")))
                .andExpect(jsonPath("$.phone", is("+79001112233")));

        assertThat(counterpartyRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /counterparties валидирует обязательные поля")
    void createCounterpartyValidationError() throws Exception {
        Counterparty payload = new Counterparty(null, "", "", null);

        mockMvc.perform(post("/counterparties")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Counterparty name is required")))
                .andExpect(jsonPath("$.phone", is("Counterparty phone is required")));
    }

    @Test
    @DisplayName("PUT /counterparties/{id} обновляет контрагента")
    void updateCounterpartyReturnsUpdated() throws Exception {
        Counterparty existing = counterpartyRepository.save(new Counterparty(null, "ООО Старое", "+79000000000", "old"));
        Counterparty payload = new Counterparty(null, "ООО Новое", "+79009999999", null);

        mockMvc.perform(put("/counterparties/{id}", existing.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ООО Новое")))
                .andExpect(jsonPath("$.phone", is("+79009999999")))
                .andExpect(jsonPath("$.info").doesNotExist());

        Counterparty refreshed = counterpartyRepository.findById(existing.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("ООО Новое");
        assertThat(refreshed.getPhone()).isEqualTo("+79009999999");
        assertThat(refreshed.getInfo()).isNull();
    }

    @Test
    @DisplayName("PUT /counterparties/{id} возвращает 404 если нет записи")
    void updateCounterpartyNotFound() throws Exception {
        Counterparty payload = new Counterparty(null, "Имя", "+79001112233", null);

        mockMvc.perform(put("/counterparties/{id}", 999)
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /counterparties/{id} удаляет контрагента")
    void deleteCounterpartyReturnsNoContent() throws Exception {
        Counterparty counterparty = counterpartyRepository.save(new Counterparty(null, "ООО Удалить", "+79005553322", null));

        mockMvc.perform(delete("/counterparties/{id}", counterparty.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(counterpartyRepository.findById(counterparty.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /counterparties/{id} возвращает 404 если нет записи")
    void deleteCounterpartyNotFound() throws Exception {
        mockMvc.perform(delete("/counterparties/{id}", 1234)
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNotFound());
    }

    private String obtainToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(PASSWORD);

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
