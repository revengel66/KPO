package com.example.kpo.service;

import com.example.kpo.entity.Counterparty;
import com.example.kpo.repository.CounterpartyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CounterpartyService {

    private final CounterpartyRepository counterpartyRepository;

    public CounterpartyService(CounterpartyRepository counterpartyRepository) {
        this.counterpartyRepository = counterpartyRepository;
    }

    public List<Counterparty> getAll() {
        return counterpartyRepository.findAll();
    }

    public Optional<Counterparty> getById(Long id) {
        return counterpartyRepository.findById(id);
    }

    public Counterparty create(Counterparty counterparty) {
        return counterpartyRepository.save(counterparty);
    }

    public Optional<Counterparty> update(Long id, Counterparty counterparty) {
        return counterpartyRepository.findById(id)
                .map(existing -> {
                    existing.setName(counterparty.getName());
                    existing.setPhone(counterparty.getPhone());
                    existing.setInfo(counterparty.getInfo());
                    return counterpartyRepository.save(existing);
                });
    }

    public void delete(Long id) {
        counterpartyRepository.deleteById(id);
    }
}
