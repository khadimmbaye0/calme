package com.calme.journal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentActionRepository extends JpaRepository<AgentAction, Long> {

    List<AgentAction> findTop30ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<AgentAction> findByIdAndUserId(Long id, Long userId);

    Optional<AgentAction> findFirstByUserIdAndStatusOrderByCreatedAtDescIdDesc(
            Long userId, AgentActionStatus status);
}
