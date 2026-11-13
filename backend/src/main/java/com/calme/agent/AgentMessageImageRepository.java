package com.calme.agent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentMessageImageRepository extends JpaRepository<AgentMessageImage, Long> {

    Optional<AgentMessageImage> findByMessage_Id(Long messageId);

    @Query("select image.message.id from AgentMessageImage image where image.message.id in :messageIds")
    List<Long> findMessageIds(@Param("messageIds") Collection<Long> messageIds);

    @Modifying
    @Query("delete from AgentMessageImage image where image.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") java.time.Instant cutoff);
}
