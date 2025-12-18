package com.example.kpo.repository.projection;

import java.time.LocalDate;

public interface DailyDemandAggregate {

    LocalDate getDate();

    Long getQuantity();
}
