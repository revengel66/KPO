package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.entity.Admin;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.repository.ProductRepository;
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
class ProductControllerIntegrationTest {

    private static final String USERNAME = "PRODUCT_TEST";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("GET /products возвращает все товары")
    void getAllProductsReturnsData() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Основной", "info"));
        Product first = productRepository.save(new Product(null, "Товар 1", 5, warehouse));
        Product second = productRepository.save(new Product(null, "Товар 2", 10, warehouse));

        mockMvc.perform(get("/products")
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.getId().intValue(),
                        second.getId().intValue()
                )));
    }

    @Test
    @DisplayName("GET /products/{id} возвращает товар по идентификатору")
    void getProductByIdReturnsEntity() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад", "инфо"));
        Product product = productRepository.save(new Product(null, "Компьютер", 3, warehouse));

        mockMvc.perform(get("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Компьютер")))
                .andExpect(jsonPath("$.count", is(3)))
                .andExpect(jsonPath("$.warehouse.id", is(warehouse.getId().intValue())));
    }

    @Test
    @DisplayName("POST /products создаёт товар")
    void createProductReturnsCreated() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад", "инфо"));
        Warehouse warehouseReference = new Warehouse();
        warehouseReference.setId(warehouse.getId());
        Product payload = new Product(null, "Телефон", 7, warehouseReference);

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name", is("Телефон")))
                .andExpect(jsonPath("$.count", is(7)))
                .andExpect(jsonPath("$.warehouse.id", is(warehouse.getId().intValue())));

        assertThat(productRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /products проверяет валидацию данных")
    void createProductValidationError() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад", "инфо"));
        Warehouse ref = new Warehouse();
        ref.setId(warehouse.getId());
        Product payload = new Product(null, "", 5, ref);

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", is("Product name is required")));
    }

    @Test
    @DisplayName("POST /products возвращает 404 при отсутствии склада")
    void createProductWarehouseNotFound() throws Exception {
        Warehouse ref = new Warehouse();
        ref.setId(999L);
        Product payload = new Product(null, "Стол", 1, ref);

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Warehouse not found")));
    }

    @Test
    @DisplayName("PUT /products/{id} обновляет товар")
    void updateProductReturnsUpdated() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад", "инфо"));
        Warehouse updatedWarehouse = warehouseRepository.save(new Warehouse(null, "Новый склад", "info2"));
        Product product = productRepository.save(new Product(null, "Старое имя", 5, warehouse));

        Warehouse ref = new Warehouse();
        ref.setId(updatedWarehouse.getId());
        Product payload = new Product(null, "Новое имя", 9, ref);

        mockMvc.perform(put("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Новое имя")))
                .andExpect(jsonPath("$.count", is(9)))
                .andExpect(jsonPath("$.warehouse.id", is(updatedWarehouse.getId().intValue())));

        Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Новое имя");
        assertThat(refreshed.getCount()).isEqualTo(9);
        assertThat(refreshed.getWarehouse().getId()).isEqualTo(updatedWarehouse.getId());
    }

    @Test
    @DisplayName("PUT /products/{id} возвращает 404 если товара нет")
    void updateProductNotFound() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад", "инфо"));
        Warehouse ref = new Warehouse();
        ref.setId(warehouse.getId());
        Product payload = new Product(null, "Имя", 1, ref);

        mockMvc.perform(put("/products/{id}", 12345)
                        .header("Authorization", "Bearer " + obtainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /products/{id} удаляет товар")
    void deleteProductReturnsNoContent() throws Exception {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Склад", "инфо"));
        Product product = productRepository.save(new Product(null, "Удаляемый", 2, warehouse));

        mockMvc.perform(delete("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + obtainToken()))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findById(product.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /products/{id} возвращает 404 если товара нет")
    void deleteProductNotFound() throws Exception {
        mockMvc.perform(delete("/products/{id}", 999)
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
