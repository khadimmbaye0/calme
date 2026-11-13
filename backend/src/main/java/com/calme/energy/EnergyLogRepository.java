package com.calme.energy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnergyLogRepository extends JpaRepository<EnergyLog, Long> {

    Optional<EnergyLog> findFirstByUserIdAndEnergyDateOrderByIdDesc(Long userId, LocalDate energyDate);

    List<EnergyLog> findTop30ByUserIdOrderByEnergyDateDescIdDesc(Long userId);
}
