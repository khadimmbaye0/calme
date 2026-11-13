package com.calme.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    List<AgentMessage> findTop30ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<AgentMessage> findByIdAndUserId(Long id, Long userId);
}
