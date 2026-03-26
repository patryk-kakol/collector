package org.pk.collector.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "system_state")
@Getter
@Setter
public class SystemState {
    @Id
    private String id;

    @Column(name = "last_completed_at")
    private Instant lastCompletedAt;
}