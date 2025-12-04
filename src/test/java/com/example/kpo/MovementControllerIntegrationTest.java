package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Counterparty;
import com.example.kpo.entity.Employee;
import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.CounterpartyRepository;
import com.example.kpo.repository.EmployeeRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.ProductRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MovementControllerIntegrationTest {

    private static final String USERNAME = "MOVEMENT_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Product product;
    private Employee employee;
    private Employee targetEmployee;
    private Counterparty counterparty;
    private Warehouse sourceWarehouse;
    private Warehouse targetWarehouse;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        productRepository.deleteAll();
        employeeRepository.deleteAll();
        counterpartyRepository.deleteAll();
        warehouseRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);

        sourceWarehouse = warehouseRepository.save(new Warehouse(null, "Склад A", "источник"));
        targetWarehouse = warehouseRepository.save(new Warehouse(null, "Склад B", "приём"));
        employee = employeeRepository.save(new Employee(null, "Иван", "+79000000001", "инициатор"));
        targetEmployee = employeeRepository.save(new Employee(null, "Пётр", "+79000000002", "получатель"));
        counterparty = counterpartyRepository.save(new Counterparty(null, "ООО Поставщик", "+79001234567", "поставщик"));
        product = productRepository.save(new Product(null, "Товар", 5, sourceWarehouse));
    }

    @Test
    @DisplayName("GET /movements возвращает список операций")
    void getAllMovementsReturnsData() throws Exception {
        Movement inbound = movementRepository.save(new Movement(null, LocalDate.now(), MovementType.INBOUND,
                "приход", product, employee, counterparty, sourceWarehouse, null, null));
        Movement outbound = movementRepository.save(new Movement(null, LocalDate.now(), MovementType.OUTBOUND,
                "расход", product, employee, counterparty, sourceWarehouse, null, null));

        mockMvc.perform(get("/movements")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        inbound.getId().intValue(),
                        outbound.getId().intValue()
                )));
    }

    @Test
    @DisplayName("GET /movements/{id} возвращает операцию по идентификатору")
    void getMovementByIdReturnsEntity() throws Exception {
        Movement movement = movementRepository.save(new Movement(null, LocalDate.now(), MovementType.INBOUND,
                "инфо", product, employee, counterparty, sourceWarehouse, null, null));

        mockMvc.perform(get("/movements/{id}", movement.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(movement.getId().intValue())))
                .andExpect(jsonPath("$.type", is("INBOUND")))
                .andExpect(jsonPath("$.product.id", is(product.getId().intValue())));
    }

    @Test
    @DisplayName("POST /movements создаёт приход (INBOUND)")
    void createInboundMovementReturnsCreated() throws Exception {
        Movement payload = new Movement();
        payload.setDate(LocalDate.now());
        payload.setType(MovementType.INBOUND);
        payload.setInfo("приход");
        payload.setProduct(refProduct(product));
        payload.setEmployee(refEmployee(employee));
        payload.setCounterparty(refCounterparty(counterparty));
        payload.setWarehouse(refWarehouse(sourceWarehouse));

        mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("INBOUND")))
                .andExpect(jsonPath("$.counterparty.id", is(counterparty.getId().intValue())))
                .andExpect(jsonPath("$.targetWarehouse").doesNotExist());

        assertThat(movementRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /movements создаёт перемещение (TRANSFER)")
    void createTransferMovementReturnsCreated() throws Exception {
        Movement payload = new Movement();
        payload.setDate(LocalDate.now());
        payload.setType(MovementType.TRANSFER);
        payload.setInfo("перемещение");
        payload.setProduct(refProduct(product));
        payload.setEmployee(refEmployee(employee));
        payload.setWarehouse(refWarehouse(sourceWarehouse));
        payload.setTargetEmployee(refEmployee(targetEmployee));
        payload.setTargetWarehouse(refWarehouse(targetWarehouse));

        mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("TRANSFER")))
                .andExpect(jsonPath("$.targetEmployee.id", is(targetEmployee.getId().intValue())))
                .andExpect(jsonPath("$.counterparty").doesNotExist());
    }

    @Test
    @DisplayName("POST /movements проверяет правила типов")
    void createTransferMovementValidationError() throws Exception {
        Movement payload = new Movement();
        payload.setDate(LocalDate.now());
        payload.setType(MovementType.TRANSFER);
        payload.setProduct(refProduct(product));
        payload.setEmployee(refEmployee(employee));
        payload.setWarehouse(refWarehouse(sourceWarehouse));
        payload.setCounterparty(refCounterparty(counterparty)); // запрещено для transfer

        mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Counterparty must be empty for transfer movement")));
    }

    @Test
    @DisplayName("PUT /movements/{id} обновляет операцию")
    void updateMovementReturnsUpdated() throws Exception {
        Movement existing = movementRepository.save(new Movement(null, LocalDate.now(), MovementType.INBOUND,
                "старое", product, employee, counterparty, sourceWarehouse, null, null));

        Movement payload = new Movement();
        payload.setDate(LocalDate.now().plusDays(1));
        payload.setType(MovementType.INBOUND);
        payload.setInfo("обновлено");
        payload.setProduct(refProduct(product));
        payload.setEmployee(refEmployee(employee));
        payload.setCounterparty(refCounterparty(counterparty));
        payload.setWarehouse(refWarehouse(targetWarehouse));

        mockMvc.perform(put("/movements/{id}", existing.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info", is("обновлено")))
                .andExpect(jsonPath("$.warehouse.id", is(targetWarehouse.getId().intValue())));

        Movement refreshed = movementRepository.findById(existing.getId()).orElseThrow();
        assertThat(refreshed.getInfo()).isEqualTo("обновлено");
    }

    @Test
    @DisplayName("DELETE /movements/{id} удаляет операцию")
    void deleteMovementReturnsNoContent() throws Exception {
        Movement movement = movementRepository.save(new Movement(null, LocalDate.now(), MovementType.INBOUND,
                "удалить", product, employee, counterparty, sourceWarehouse, null, null));

        mockMvc.perform(delete("/movements/{id}", movement.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(movementRepository.findById(movement.getId())).isEmpty();
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

    private Warehouse refWarehouse(Warehouse saved) {
        Warehouse ref = new Warehouse();
        ref.setId(saved.getId());
        return ref;
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
