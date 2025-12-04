package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Employee;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.kpo.repository.MovementRepository;
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
class EmployeeControllerIntegrationTest {

    private static final String USERNAME = "EMPLOYEE_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

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
        employeeRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("GET /employees возвращает всех сотрудников")
    void getAllEmployeesReturnsData() throws Exception {
        Employee first = employeeRepository.save(new Employee(null, "Иван", "+79000000001", "info1"));
        Employee second = employeeRepository.save(new Employee(null, "Петр", "+79000000002", null));

        mockMvc.perform(get("/employees")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.getId().intValue(),
                        second.getId().intValue()
                )));
    }

    @Test
    @DisplayName("GET /employees/{id} возвращает сотрудника по id")
    void getEmployeeByIdReturnsEntity() throws Exception {
        Employee employee = employeeRepository.save(new Employee(null, "Мария", "+79001234567", "notes"));

        mockMvc.perform(get("/employees/{id}", employee.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(employee.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Мария")))
                .andExpect(jsonPath("$.phone", is("+79001234567")))
                .andExpect(jsonPath("$.info", is("notes")));
    }

    @Test
    @DisplayName("POST /employees создаёт сотрудника")
    void createEmployeeReturnsCreated() throws Exception {
        Employee payload = new Employee(null, "Алексей", "+79001112233", null);

        mockMvc.perform(post("/employees")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Алексей")))
                .andExpect(jsonPath("$.phone", is("+79001112233")));

        assertThat(employeeRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /employees валидирует обязательные поля")
    void createEmployeeValidationError() throws Exception {
        Employee payload = new Employee(null, "", "", null);

        mockMvc.perform(post("/employees")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Employee name is required")))
                .andExpect(jsonPath("$.phone", is("Employee phone is required")));
    }

    @Test
    @DisplayName("PUT /employees/{id} обновляет сотрудника")
    void updateEmployeeReturnsUpdated() throws Exception {
        Employee existing = employeeRepository.save(new Employee(null, "Старое имя", "+79000000000", "old"));
        Employee payload = new Employee(null, "Новое имя", "+79009999999", null);

        mockMvc.perform(put("/employees/{id}", existing.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Новое имя")))
                .andExpect(jsonPath("$.phone", is("+79009999999")))
                .andExpect(jsonPath("$.info").doesNotExist());

        Employee refreshed = employeeRepository.findById(existing.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Новое имя");
        assertThat(refreshed.getPhone()).isEqualTo("+79009999999");
        assertThat(refreshed.getInfo()).isNull();
    }

    @Test
    @DisplayName("PUT /employees/{id} возвращает 404 если нет записи")
    void updateEmployeeNotFound() throws Exception {
        Employee payload = new Employee(null, "Имя", "+79001112233", null);

        mockMvc.perform(put("/employees/{id}", 999)
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /employees/{id} удаляет сотрудника")
    void deleteEmployeeReturnsNoContent() throws Exception {
        Employee employee = employeeRepository.save(new Employee(null, "Удалить", "+79005553322", null));

        mockMvc.perform(delete("/employees/{id}", employee.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(employeeRepository.findById(employee.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /employees/{id} возвращает 404 если нет записи")
    void deleteEmployeeNotFound() throws Exception {
        mockMvc.perform(delete("/employees/{id}", 1234)
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
