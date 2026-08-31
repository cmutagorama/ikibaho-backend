package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.UserSummary;
import com.charlie.ikibaho.identity.internal.domain.MembershipStatus;
import com.charlie.ikibaho.identity.internal.domain.Organization;
import com.charlie.ikibaho.identity.internal.domain.OrganizationMember;
import com.charlie.ikibaho.identity.internal.domain.User;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationMemberRepository;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserGroupRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final UserGroupRepository groups;
    private final OrganizationMemberRepository members;
    private final OrganizationRepository organizations;

    public UserServiceImpl(UserRepository users, UserGroupRepository groups,
                           OrganizationMemberRepository members,
                           OrganizationRepository organizations) {
        this.users = users;
        this.groups = groups;
        this.members = members;
        this.organizations = organizations;
    }

    @Override
    public Optional<UserSummary> findById(UUID id) {
        return users.findById(id).map(UserServiceImpl::toSummary);
    }

    @Override
    public List<UserSummary> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();          // findAllById(empty) still round-trips; skip it
        }
        return users.findAllById(ids).stream()
                .map(UserServiceImpl::toSummary)
                .toList();
    }

    @Override
    public boolean userExistsInOrganization(UUID userId, UUID organizationId) {
        return members.existsByUserIdAndOrganizationIdAndStatus(
                userId, organizationId, MembershipStatus.ACTIVE);
    }

    @Override
    public boolean groupExistsInOrganization(UUID groupId, UUID organizationId) {
        return groups.existsByIdAndOrganizationId(groupId, organizationId);
    }

    @Override
    public List<OrganizationMembership> membershipsOf(UUID userId) {
        List<OrganizationMember> active = members.activeFor(userId);
        if (active.isEmpty()) {
            return List.of();
        }
        // One batch read of the organizations, not one per membership.
        Map<UUID, Organization> byId = organizations
                .findAllById(active.stream().map(OrganizationMember::getOrganizationId).toList())
                .stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));

        return active.stream()
                .map(m -> toMembership(m, byId.get(m.getOrganizationId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<OrganizationMembership> membership(UUID userId, UUID organizationId) {
        return members.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .flatMap(m -> organizations.findById(organizationId).map(org -> toMembership(m, org)));
    }

    private static OrganizationMembership toMembership(OrganizationMember member, Organization org) {
        return org == null
                ? null
                : new OrganizationMembership(org.getId(), org.getSlug(), org.getName(),
                member.getRole().name());
    }

    /**
     * The module boundary. Nothing outside identity ever sees a User entity --
     * only this record, which carries no password hash, no status, no JPA session.
     */
    private static UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl());
    }
}
