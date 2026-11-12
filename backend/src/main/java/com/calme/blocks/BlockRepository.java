package com.calme.blocks;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findFirstByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<Block> findByUserIdOrderByStartDateAsc(Long userId);
}
