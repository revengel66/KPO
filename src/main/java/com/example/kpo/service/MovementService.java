package com.example.kpo.service;

import com.example.kpo.entity.Counterparty;
import com.example.kpo.entity.Employee;
import com.example.kpo.entity.Movement;
import com.example.kpo.entity.MovementType;
import com.example.kpo.entity.Product;
import com.example.kpo.entity.Warehouse;
import com.example.kpo.repository.CounterpartyRepository;
import com.example.kpo.repository.EmployeeRepository;
import com.example.kpo.repository.MovementRepository;
import com.example.kpo.repository.ProductRepository;
import com.example.kpo.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MovementService {

    private final MovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final EmployeeRepository employeeRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final WarehouseRepository warehouseRepository;

    public MovementService(MovementRepository movementRepository,
                           ProductRepository productRepository,
                           EmployeeRepository employeeRepository,
                           CounterpartyRepository counterpartyRepository,
                           WarehouseRepository warehouseRepository) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.employeeRepository = employeeRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public List<Movement> getAllMovements() {
        return movementRepository.findAll();
    }

    public Optional<Movement> getMovementById(Long id) {
        return movementRepository.findById(id);
    }

    public Movement createMovement(Movement movement) {
        Movement prepared = new Movement();
        copyAndResolveRelations(movement, prepared);
        validateRelations(prepared);
        return movementRepository.save(prepared);
    }

    public Optional<Movement> updateMovement(Long id, Movement movement) {
        return movementRepository.findById(id)
                .map(existing -> {
                    copyAndResolveRelations(movement, existing);
                    validateRelations(existing);
                    return movementRepository.save(existing);
                });
    }

    public void deleteMovement(Long id) {
        movementRepository.deleteById(id);
    }

    private void copyAndResolveRelations(Movement source, Movement target) {
        target.setDate(source.getDate());
        target.setType(source.getType());
        target.setInfo(source.getInfo());
        target.setProduct(resolveProduct(source.getProduct()));
        target.setEmployee(resolveRequiredEmployee(source.getEmployee(), "employee"));
        target.setCounterparty(resolveOptionalCounterparty(source.getCounterparty()));
        target.setWarehouse(resolveRequiredWarehouse(source.getWarehouse(), "warehouse"));
        target.setTargetEmployee(resolveOptionalEmployee(source.getTargetEmployee(), "targetEmployee"));
        target.setTargetWarehouse(resolveOptionalWarehouse(source.getTargetWarehouse(), "targetWarehouse"));
    }

    private Product resolveProduct(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Product id is required");
        }
        return productRepository.findById(product.getId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    }

    private Employee resolveRequiredEmployee(Employee employee, String field) {
        if (employee == null || employee.getId() == null) {
            throw new IllegalArgumentException("Employee id is required for " + field);
        }
        return employeeRepository.findById(employee.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private Employee resolveOptionalEmployee(Employee employee, String field) {
        if (employee == null) {
            return null;
        }
        if (employee.getId() == null) {
            throw new IllegalArgumentException("Employee id is required for " + field);
        }
        return employeeRepository.findById(employee.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private Counterparty resolveOptionalCounterparty(Counterparty counterparty) {
        if (counterparty == null) {
            return null;
        }
        if (counterparty.getId() == null) {
            throw new IllegalArgumentException("Counterparty id is required");
        }
        return counterpartyRepository.findById(counterparty.getId())
                .orElseThrow(() -> new EntityNotFoundException("Counterparty not found"));
    }

    private Warehouse resolveRequiredWarehouse(Warehouse warehouse, String field) {
        if (warehouse == null || warehouse.getId() == null) {
            throw new IllegalArgumentException("Warehouse id is required for " + field);
        }
        return warehouseRepository.findById(warehouse.getId())
                .orElseThrow(() -> new EntityNotFoundException("Warehouse not found"));
    }

    private Warehouse resolveOptionalWarehouse(Warehouse warehouse, String field) {
        if (warehouse == null) {
            return null;
        }
        if (warehouse.getId() == null) {
            throw new IllegalArgumentException("Warehouse id is required for " + field);
        }
        return warehouseRepository.findById(warehouse.getId())
                .orElseThrow(() -> new EntityNotFoundException("Warehouse not found"));
    }

    private void validateRelations(Movement movement) {
        MovementType type = movement.getType();
        if (movement.getDate() == null) {
            throw new IllegalArgumentException("Movement date is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Movement type is required");
        }
        switch (type) {
            case INBOUND -> validateInbound(movement);
            case OUTBOUND -> validateOutbound(movement);
            case TRANSFER -> validateTransfer(movement);
            default -> throw new IllegalArgumentException("Unsupported movement type");
        }
    }

    private void validateInbound(Movement movement) {
        if (movement.getCounterparty() == null) {
            throw new IllegalArgumentException("Counterparty is required for inbound movement");
        }
        if (movement.getTargetEmployee() != null || movement.getTargetWarehouse() != null) {
            throw new IllegalArgumentException("Target employee/warehouse must be empty for inbound movement");
        }
    }

    private void validateOutbound(Movement movement) {
        if (movement.getCounterparty() == null) {
            throw new IllegalArgumentException("Counterparty is required for outbound movement");
        }
        if (movement.getTargetEmployee() != null || movement.getTargetWarehouse() != null) {
            throw new IllegalArgumentException("Target employee/warehouse must be empty for outbound movement");
        }
    }

    private void validateTransfer(Movement movement) {
        if (movement.getCounterparty() != null) {
            throw new IllegalArgumentException("Counterparty must be empty for transfer movement");
        }
        if (movement.getTargetEmployee() == null) {
            throw new IllegalArgumentException("Target employee is required for transfer movement");
        }
        if (movement.getTargetWarehouse() == null) {
            throw new IllegalArgumentException("Target warehouse is required for transfer movement");
        }
    }
}
