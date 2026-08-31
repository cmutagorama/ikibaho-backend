package com.charlie.ikibaho.notification.internal.persistence;

import com.charlie.ikibaho.notification.internal.domain.DigestState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DigestStateRepository extends JpaRepository<DigestState, UUID> {
}
