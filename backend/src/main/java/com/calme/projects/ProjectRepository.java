package com.calme.projects;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByUserIdOrderByDeadlineAsc(Long userId);

    Optional<Project> findByIdAndUserId(Long id, Long userId);
}
