package org.pk.collector.repository;

import org.pk.collector.domain.SystemState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemStateRepository extends JpaRepository<SystemState, String> {}
