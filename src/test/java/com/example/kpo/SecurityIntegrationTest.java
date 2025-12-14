package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.CounterpartyRepository;
import com.example.kpo.repository.EmployeeRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseProductRepository;
import com.example.kpo.repository.WarehouseRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String USERNAME = "SEC_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private MovementRepository movementRepository;
    @Autowired
    private WarehouseProductRepository warehouseProductRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        warehouseProductRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        employeeRepository.deleteAll();
        counterpartyRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("Запрос без токена получает статус 401, а с токеном - 200")
    void securedEndpointsRequireAuthorization() throws Exception {
        mockMvc.perform(get("/warehouses"))
                .andExpect(status().isForbidden());

        String token = obtainToken();

        mockMvc.perform(get("/warehouses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String obtainToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(USERNAME);
        loginRequest.setPassword(PASSWORD);

        MvcResult response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
