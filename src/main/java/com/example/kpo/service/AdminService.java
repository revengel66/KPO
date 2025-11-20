package com.example.kpo.service;

import com.example.kpo.entity.Admin;
import com.example.kpo.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AdminService{
    private AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    public void createAdminIfEmptuDB(){
        if (adminRepository.count() == 0){
            adminRepository.save(new Admin(null,"ADMIN",""));
        }
    }
}
