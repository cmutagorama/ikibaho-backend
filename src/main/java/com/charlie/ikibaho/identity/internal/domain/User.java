package com.charlie.ikibaho.identity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class User extends BaseEntity {
    @Column(nullable = false)
    private String email;

    /**
     * Null for federated (SSO/LDAP) accounts, which authenticate elsewhere.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "has_federated_identity", nullable = false)
    private boolean hasFederatedIdentity;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
        this.emailVerified = false;
    }

    /**
     * An account created by invitation: no password yet, so INVITED rather than
     * ACTIVE. ck_app_user_authenticatable permits a null hash only in that state.
     * <p>
     * No organization here -- the membership is a separate row, which is what
     * lets the same account be invited into a second workspace later without
     * duplicating the person.
     */
    public static User invited(String email, String displayName) {
        User user = new User();
        user.email = normalizeEmail(email);
        user.displayName = displayName;
        user.status = UserStatus.INVITED;
        user.emailVerified = false;
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Accepting an invitation both sets the password and proves control of the
     * address the link was sent to, so the account becomes verified and active.
     */
    public void activateWithPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
        this.emailVerified = true;
    }

    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * False for federated accounts: there is no local secret to check.
     */
    public boolean canAuthenticateWithPassword() {
        return passwordHash != null;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    /**
     * Activate an account whose only credential is an external identity.
     * <p>
     * The provider asserted the address, which is stronger evidence than a
     * password would be -- so the account is verified as well as active.
     */
    public void activateWithFederatedIdentity() {
        this.hasFederatedIdentity = true;
        this.status = UserStatus.ACTIVE;
        this.emailVerified = true;
    }

    public void setHasFederatedIdentity(boolean value) {
        this.hasFederatedIdentity = value;
    }

    public boolean hasFederatedIdentity() {
        return hasFederatedIdentity;
    }

    /**
     * True when removing one credential would still leave a way in.
     */
    public boolean hasAnotherCredentialBesides(boolean federated) {
        return federated ? canAuthenticateWithPassword() : hasFederatedIdentity;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
