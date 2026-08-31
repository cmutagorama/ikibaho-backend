package com.charlie.ikibaho.board.internal.persistence;

import com.charlie.ikibaho.board.internal.domain.Sprint;
import com.charlie.ikibaho.board.internal.domain.SprintState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    Optional<Sprint> findByProjectIdAndState(UUID projectId, SprintState state);

    List<Sprint> findByProjectIdOrderByStateAscCreatedAtAsc(UUID projectId);

    List<Sprint> findByProjectIdAndStateNot(UUID projectId, SprintState state);
}
