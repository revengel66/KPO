package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Category;
import com.example.kpo.entity.Counterparty;
import com.example.kpo.entity.Employee;
import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementProduct;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.entity.WarehouseProduct;
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

import java.time.LocalDateTime;
import java.util.List;

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
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseProductRepository warehouseProductRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Product product;
    private Category productCategory;
    private Employee employee;
    private Employee targetEmployee;
    private Counterparty counterparty;
    private Warehouse sourceWarehouse;
    private Warehouse targetWarehouse;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        warehouseProductRepository.deleteAll();
        productRepository.deleteAll();
        employeeRepository.deleteAll();
        counterpartyRepository.deleteAll();
        warehouseRepository.deleteAll();
        categoryRepository.deleteAll();
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
        productCategory = categoryRepository.save(new Category(null, "Перемещение"));
        product = new Product(null, "Товар", "описание");
        product.setCategory(productCategory);
        productRepository.save(product);
    }

    @Test
    @DisplayName("GET /movements возвращает список операций")
    void getAllMovementsReturnsData() throws Exception {
        Movement inbound = createMovementEntity(MovementType.INBOUND, "приход",
                sourceWarehouse, null, employee, null, counterparty, product, 5);
        Movement outbound = createMovementEntity(MovementType.OUTBOUND, "расход",
                sourceWarehouse, null, employee, null, counterparty, product, 2);

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
        Movement movement = createMovementEntity(MovementType.INBOUND, "инфо",
                sourceWarehouse, null, employee, null, counterparty, product, 3);

        mockMvc.perform(get("/movements/{id}", movement.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(movement.getId().intValue())))
                .andExpect(jsonPath("$.type", is("INBOUND")))
                .andExpect(jsonPath("$.items[0].product.id", is(product.getId().intValue())));
    }

    @Test
    @DisplayName("POST /movements создаёт приход (INBOUND)")
    void createInboundMovementReturnsCreated() throws Exception {
        Movement payload = buildMovementPayload(MovementType.INBOUND, sourceWarehouse, null,
                employee, null, counterparty, product, 5);

        mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("INBOUND")))
                .andExpect(jsonPath("$.counterparty.id", is(counterparty.getId().intValue())))
                .andExpect(jsonPath("$.items[0].quantity", is(5)))
                .andExpect(jsonPath("$.targetWarehouse").doesNotExist());

        WarehouseProduct stock = warehouseProductRepository.findByWarehouseAndProduct(sourceWarehouse, product).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("POST /movements создаёт перемещение (TRANSFER)")
    void createTransferMovementReturnsCreated() throws Exception {
        warehouseProductRepository.save(new WarehouseProduct(sourceWarehouse, product, 10));
        Movement payload = buildMovementPayload(MovementType.TRANSFER, sourceWarehouse, targetWarehouse,
                employee, targetEmployee, null, product, 3);

        mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("TRANSFER")))
                .andExpect(jsonPath("$.targetEmployee.id", is(targetEmployee.getId().intValue())))
                .andExpect(jsonPath("$.counterparty").doesNotExist());

        WarehouseProduct sourceStock = warehouseProductRepository.findByWarehouseAndProduct(sourceWarehouse, product).orElseThrow();
        WarehouseProduct targetStock = warehouseProductRepository.findByWarehouseAndProduct(targetWarehouse, product).orElseThrow();
        assertThat(sourceStock.getQuantity()).isEqualTo(7);
        assertThat(targetStock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("POST /movements проверяет правила типов")
    void createTransferMovementValidationError() throws Exception {
        Movement payload = buildMovementPayload(MovementType.TRANSFER, sourceWarehouse, targetWarehouse,
                employee, targetEmployee, counterparty, product, 1);

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
        Movement initial = buildMovementPayload(MovementType.INBOUND, sourceWarehouse, null,
                employee, null, counterparty, product, 4);
        Long existingId = createMovementThroughApi(initial);

        Movement payload = buildMovementPayload(MovementType.INBOUND, targetWarehouse, null,
                employee, null, counterparty, product, 6);
        payload.setInfo("обновлено");

        mockMvc.perform(put("/movements/{id}", existingId)
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info", is("обновлено")))
                .andExpect(jsonPath("$.warehouse.id", is(targetWarehouse.getId().intValue())));

        Movement refreshed = movementRepository.findById(existingId).orElseThrow();
        assertThat(refreshed.getInfo()).isEqualTo("обновлено");
        WarehouseProduct sourceStock = warehouseProductRepository.findByWarehouseAndProduct(sourceWarehouse, product).orElseThrow();
        WarehouseProduct newStock = warehouseProductRepository.findByWarehouseAndProduct(targetWarehouse, product).orElseThrow();
        assertThat(sourceStock.getQuantity()).isEqualTo(0);
        assertThat(newStock.getQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("DELETE /movements/{id} удаляет операцию")
    void deleteMovementReturnsNoContent() throws Exception {
        Movement payload = buildMovementPayload(MovementType.INBOUND, sourceWarehouse, null,
                employee, null, counterparty, product, 2);
        Long movementId = createMovementThroughApi(payload);

        mockMvc.perform(delete("/movements/{id}", movementId)
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(movementRepository.findById(movementId)).isEmpty();
        WarehouseProduct stock = warehouseProductRepository.findByWarehouseAndProduct(sourceWarehouse, product).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(0);
    }

    private Movement createMovementEntity(MovementType type,
                                          String info,
                                          Warehouse warehouse,
                                          Warehouse targetWarehouse,
                                          Employee employee,
                                          Employee targetEmployee,
                                          Counterparty counterparty,
                                          Product product,
                                          int quantity) {
        Movement movement = new Movement();
        movement.setDate(LocalDateTime.now());
        movement.setType(type);
        movement.setInfo(info);
        movement.setWarehouse(warehouse);
        movement.setTargetWarehouse(targetWarehouse);
        movement.setEmployee(employee);
        movement.setTargetEmployee(targetEmployee);
        movement.setCounterparty(counterparty);
        MovementProduct item = new MovementProduct();
        item.setMovement(movement);
        item.setProduct(product);
        item.setQuantity(quantity);
        movement.getItems().add(item);
        return movementRepository.save(movement);
    }

    private Movement buildMovementPayload(MovementType type,
                                          Warehouse warehouse,
                                          Warehouse targetWarehouse,
                                          Employee employee,
                                          Employee targetEmployee,
                                          Counterparty counterparty,
                                          Product product,
                                          int quantity) {
        Movement movement = new Movement();
        movement.setDate(LocalDateTime.now());
        movement.setType(type);
        movement.setWarehouse(refWarehouse(warehouse));
        movement.setTargetWarehouse(targetWarehouse != null ? refWarehouse(targetWarehouse) : null);
        movement.setEmployee(refEmployee(employee));
        movement.setTargetEmployee(targetEmployee != null ? refEmployee(targetEmployee) : null);
        movement.setCounterparty(counterparty != null ? refCounterparty(counterparty) : null);
        MovementProduct item = new MovementProduct();
        item.setProduct(refProduct(product));
        item.setQuantity(quantity);
        movement.setItems(List.of(item));
        return movement;
    }

    private Long createMovementThroughApi(Movement payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/movements")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
