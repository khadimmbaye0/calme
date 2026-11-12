package com.calme.calendar;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByUserIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            Long userId, Instant endExclusive, Instant startInclusive);

    Optional<Event> findByIdAndUserId(Long id, Long userId);

    List<Event> findByTaskIdIn(Collection<Long> taskIds);
}
