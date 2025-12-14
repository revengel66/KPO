package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.dto.StockReportRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Category;
import com.example.kpo.entity.Counterparty;
import com.example.kpo.entity.Employee;
import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementProduct;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CategoryRepository;
import com.example.kpo.repository.CounterpartyRepository;
import com.example.kpo.repository.EmployeeRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseProductRepository;
import com.example.kpo.repository.WarehouseRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemWorkflowIntegrationTest {

    private static final String USERNAME = "SYSTEM_TEST";
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
    private EmployeeRepository employeeRepository;
    @Autowired
    private CounterpartyRepository counterpartyRepository;
    @Autowired
    private MovementRepository movementRepository;
    @Autowired
    private WarehouseProductRepository warehouseProductRepository;

    private Warehouse warehouse;
    private Product product;
    private Employee employee;
    private Counterparty counterparty;

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

        warehouse = warehouseRepository.save(new Warehouse(null, "Склад «Интеграционный»", "Тестовый поток"));

        Category category = categoryRepository.save(new Category(null, "Гаджеты"));
        product = new Product(null, "Тестовый планшет", "workflow");
        product.setCategory(category);
        product = productRepository.save(product);

        employee = employeeRepository.save(new Employee(null, "QA Engineer", "+79000000003", "E2E"));
        counterparty = counterpartyRepository.save(new Counterparty(null, "ООО «Системы»", "+79004561234", "workflow"));
    }

    @Test
    @DisplayName("Системный сценарий: приход товара и формирование отчёта")
    void runEndToEndFlow() throws Exception {
        String token = obtainToken();

        Movement inboundPayload = new Movement();
        inboundPayload.setDate(LocalDateTime.of(2025, 4, 1, 10, 0));
        inboundPayload.setType(MovementType.INBOUND);
        inboundPayload.setWarehouse(refWarehouse(warehouse));
        inboundPayload.setEmployee(refEmployee(employee));
        inboundPayload.setCounterparty(refCounterparty(counterparty));
        MovementProduct item = new MovementProduct();
        item.setProduct(refProduct(product));
        item.setQuantity(12);
        inboundPayload.setItems(List.of(item));

        mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(inboundPayload)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/warehouses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        StockReportRequest reportRequest = new StockReportRequest();
        reportRequest.setReportDate(LocalDate.of(2025, 4, 2));
        reportRequest.setWarehouseIds(List.of(warehouse.getId()));

        MvcResult reportResponse = mockMvc.perform(post("/reports/stock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(reportRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andReturn();

        assertThat(reportResponse.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    private Warehouse refWarehouse(Warehouse saved) {
        Warehouse ref = new Warehouse();
        ref.setId(saved.getId());
        return ref;
    }

    private Product refProduct(Product saved) {
        Product ref = new Product();
        ref.setId(saved.getId());
        return ref;
    }

    private Employee refEmployee(Employee saved) {
        Employee ref = new Employee();
        ref.setId(saved.getId());
        return ref;
    }

    private Counterparty refCounterparty(Counterparty saved) {
        Counterparty ref = new Counterparty();
        ref.setId(saved.getId());
        return ref;
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

        JsonNode node = objectMapper.readTree(response.getResponse().getContentAsString());
        String token = node.get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
