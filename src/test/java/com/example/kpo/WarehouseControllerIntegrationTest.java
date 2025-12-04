package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.WarehouseRepository;
import com.example.kpo.repository.MovementRepository;
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
class WarehouseControllerIntegrationTest {

    private static final String USERNAME = "TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MovementRepository movementRepository;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        warehouseRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("GET /warehouses возвращает все склады при наличии токена")
    void getAllWarehousesReturnsData() throws Exception {
        Warehouse first = warehouseRepository.save(new Warehouse(null, "Склад 1", "описание 1"));
        Warehouse second = warehouseRepository.save(new Warehouse(null, "Склад 2", "описание 2"));

        mockMvc.perform(get("/warehouses")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                first.getId().intValue(),
                                second.getId().intValue()
                        )));
    }

    @Test
    @DisplayName("GET /warehouses без токена запрещён")
    void getAllWarehousesWithoutTokenForbidden() throws Exception {
        mockMvc.perform(get("/warehouses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /warehouses/{id} возвращает конкретный склад")
    void getWarehouseByIdReturnsEntity() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Офис", "info"));

        mockMvc.perform(get("/warehouses/{id}", warehouse.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(warehouse.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Офис")))
                .andExpect(jsonPath("$.info", is("info")));
    }

    @Test
    @DisplayName("GET /warehouses/{id} возвращает 404 для несуществующего склада")
    void getWarehouseByIdNotFound() throws Exception {
        mockMvc.perform(get("/warehouses/{id}", 999)
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /warehouses создаёт новый склад")
    void createWarehouseReturnsCreated() throws Exception {
        Warehouse payload = new Warehouse(null, "Новый", "инфо");

        mockMvc.perform(post("/warehouses")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Новый")))
                .andExpect(jsonPath("$.info", is("инфо")));

        assertThat(warehouseRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /warehouses без токена запрещён")
    void createWarehouseWithoutTokenForbidden() throws Exception {
        Warehouse payload = new Warehouse(null, "Без токена", "info");

        mockMvc.perform(post("/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /warehouses отклоняет пустое имя склада")
    void createWarehouseValidationError() throws Exception {
        Warehouse payload = new Warehouse(null, "", "инфо");

        mockMvc.perform(post("/warehouses")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Warehouse name is required")));

        assertThat(warehouseRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PUT /warehouses/{id} обновляет склад")
    void updateWarehouseReturnsUpdated() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Старое имя", "старое"));
        Warehouse payload = new Warehouse(null, "Новое имя", "новое");

        mockMvc.perform(put("/warehouses/{id}", warehouse.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Новое имя")))
                .andExpect(jsonPath("$.info", is("новое")));

        Warehouse refreshed = warehouseRepository.findById(warehouse.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Новое имя");
        assertThat(refreshed.getInfo()).isEqualTo("новое");
    }

    @Test
    @DisplayName("PUT /warehouses/{id} возвращает 404 если склада нет")
    void updateWarehouseNotFound() throws Exception {
        Warehouse payload = new Warehouse(null, "Имя", "инфо");

        mockMvc.perform(put("/warehouses/{id}", 12345)
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /warehouses/{id} отклоняет пустое имя склада")
    void updateWarehouseValidationError() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Старое имя", "старое"));
        Warehouse payload = new Warehouse(null, "", "обновлённое");

        mockMvc.perform(put("/warehouses/{id}", warehouse.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Warehouse name is required")));

        Warehouse refreshed = warehouseRepository.findById(warehouse.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Старое имя");
        assertThat(refreshed.getInfo()).isEqualTo("старое");
    }

    @Test
    @DisplayName("DELETE /warehouses/{id} удаляет склад")
    void deleteWarehouseReturnsNoContent() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Удаляемый", "info"));

        mockMvc.perform(delete("/warehouses/{id}", warehouse.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(warehouseRepository.findById(warehouse.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /warehouses/{id} возвращает 404 если склада нет")
    void deleteWarehouseNotFound() throws Exception {
        mockMvc.perform(delete("/warehouses/{id}", 888)
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
