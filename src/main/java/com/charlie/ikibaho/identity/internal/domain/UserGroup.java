package com.charlie.ikibaho.identity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_group")
public class UserGroup extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    /**
     * Membership as a set of user IDs, not a @ManyToMany to User: a permission
     * check needs identifiers, and an object graph here would lazy-load whole
     * user rows on every evaluation.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "group_member",
            joinColumns = @JoinColumn(name = "group_id"),
            foreignKey = @ForeignKey(name = "fk_group_member_group"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> memberIds = new HashSet<>();

    protected UserGroup() { }   // JPA

    public UserGroup(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean addMember(UUID userId) {
        return memberIds.add(userId);       // false if already a member
    }

    public boolean removeMember(UUID userId) {
        return memberIds.remove(userId);
    }

    public boolean hasMember(UUID userId) {
        return memberIds.contains(userId);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    /** Defensive copy: mutate membership through addMember/removeMember only. */
    public Set<UUID> getMemberIds() {
        return Set.copyOf(memberIds);
    }
}
