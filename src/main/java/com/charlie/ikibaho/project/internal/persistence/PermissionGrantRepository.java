package com.charlie.ikibaho.project.internal.persistence;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.project.internal.domain.PermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissionGrantRepository extends JpaRepository<PermissionGrant, UUID> {
    List<PermissionGrant> findBySchemeId(UUID schemeId);

    void deleteBySchemeId(UUID schemeId);
}
