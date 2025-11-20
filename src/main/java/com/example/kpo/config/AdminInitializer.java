package com.example.kpo.config;

import com.example.kpo.service.AdminService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminInitializer implements CommandLineRunner {
    private AdminService adminService;

    public AdminInitializer(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public void run(String... args) throws Exception {
        adminService.createAdminIfEmptuDB();
    }
}
