package com.example.kpo;

import com.example.kpo.dto.LoginRequest;
import com.example.kpo.dto.forecast.DemandPoint;
import com.example.kpo.dto.forecast.ForecastGranularity;
import com.example.kpo.dto.forecast.ForecastMetrics;
import com.example.kpo.dto.forecast.ForecastPoint;
import com.example.kpo.dto.forecast.ForecastResult;
import com.example.kpo.entity.Admin;
import com.example.kpo.repository.AdminRepository;
import com.example.kpo.service.ForecastService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ForecastControllerIntegrationTest {

    private static final String USERNAME = "FORECAST_USER";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ForecastService forecastService;

    @BeforeEach
    void setUp() {
        adminRepository.deleteAll();
        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        adminRepository.save(admin);
    }

    @Test
    @DisplayName("GET /forecasts требует токен и возвращает прогноз")
    void getForecastReturnsData() throws Exception {
        ForecastResult stub = sampleResult();
        when(forecastService.forecastProduct(2L, 120, 14, 14, ForecastGranularity.DAY)).thenReturn(stub);

        String token = obtainToken();

        mockMvc.perform(get("/forecasts")
                        .header("Authorization", "Bearer " + token)
                        .param("productId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId", is(2)))
                .andExpect(jsonPath("$.forecast", hasSize(2)))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.metrics.mae", is(1.5)))
                .andExpect(jsonPath("$.insufficientData", is(false)));

        verify(forecastService).forecastProduct(2L, 120, 14, 14, ForecastGranularity.DAY);

    }

    private ForecastResult sampleResult() {
        List<DemandPoint> history = List.of(
                new DemandPoint(LocalDate.of(2024, 1, 1), 10),
                new DemandPoint(LocalDate.of(2024, 1, 2), 12)
        );
        List<ForecastPoint> forecast = List.of(
                new ForecastPoint(LocalDate.of(2024, 1, 3), 13.0),
                new ForecastPoint(LocalDate.of(2024, 1, 4), 14.0)
        );
        ForecastMetrics metrics = new ForecastMetrics(1.5, 12.5, 2);
        return new ForecastResult(2L, history, forecast, metrics, 0.4, 0.2, false, "HOLT");
    }

    private String obtainToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(PASSWORD);
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
