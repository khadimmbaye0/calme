package com.calme.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    List<AgentMessage> findTop30ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
