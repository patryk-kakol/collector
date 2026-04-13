package org.pk.collector.core.repository;

import org.pk.collector.core.model.JobState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStateRepository extends JpaRepository<JobState, String> {}
