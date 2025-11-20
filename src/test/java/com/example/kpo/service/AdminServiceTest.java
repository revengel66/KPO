package com.example.kpo.service;

import com.example.kpo.entity.Admin;
import com.example.kpo.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(adminRepository);
    }

    @Test
    @DisplayName("getAllAdmins возвращает список из репозитория")
    void getAllAdminsReturnsRepositoryData() {
        List<Admin> admins = List.of(new Admin(1L, "A", "pass"));
        when(adminRepository.findAll()).thenReturn(admins);

        List<Admin> result = adminService.getAllAdmins();

        assertThat(result).isEqualTo(admins);
        verify(adminRepository).findAll();
    }

    @Test
    @DisplayName("createAdminIfEmptuDB создаёт ADMIN при пустой БД")
    void createAdminIfEmptyCreatesDefaultAdmin() {
        when(adminRepository.count()).thenReturn(0L);

        adminService.createAdminIfEmptuDB();

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        Admin saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUsername()).isEqualTo("ADMIN");
        assertThat(saved.getPassword()).isEqualTo("");
    }

    @Test
    @DisplayName("createAdminIfEmptuDB не создаёт ADMIN, если записи есть")
    void createAdminIfEmptyDoesNothingWhenExists() {
        when(adminRepository.count()).thenReturn(1L);

        adminService.createAdminIfEmptuDB();

        verify(adminRepository, never()).save(any(Admin.class));
    }
}
