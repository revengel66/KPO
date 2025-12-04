package com.example.kpo.service;

import com.example.kpo.entity.Employee;
import com.example.kpo.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public List<Employee> getAll() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getById(Long id) {
        return employeeRepository.findById(id);
    }

    public Employee create(Employee employee) {
        return employeeRepository.save(employee);
    }

    public Optional<Employee> update(Long id, Employee employee) {
        return employeeRepository.findById(id)
                .map(existing -> {
                    existing.setName(employee.getName());
                    existing.setPhone(employee.getPhone());
                    existing.setInfo(employee.getInfo());
                    return employeeRepository.save(existing);
                });
    }

    public void delete(Long id) {
        employeeRepository.deleteById(id);
    }
}
