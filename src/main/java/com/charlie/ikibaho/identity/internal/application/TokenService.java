package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.identity.internal.domain.OrganizationMember;
import com.charlie.ikibaho.identity.internal.domain.RefreshToken;
import com.charlie.ikibaho.identity.internal.domain.User;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationMemberRepository;
import com.charlie.ikibaho.identity.internal.persistence.RefreshTokenRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_ID = "ikibaho-signing-key";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final OrganizationMemberRepository members;

    TokenService(JwtEncoder jwtEncoder, JwtProperties props, RefreshTokenRepository refreshTokens,
                 UserRepository users, OrganizationMemberRepository members) {
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.members = members;
    }

    /** New login into a specific workspace: fresh token family. */
    @Transactional
    public TokenPair issueForLogin(User user, OrganizationMember membership) {
        return issue(user, membership, UUID.randomUUID());
    }

    /**
     * Exchange a refresh token for a new pair.
     * Rotates on every use and revokes the whole family if a spent token is replayed.
     */
    @Transactional
    public TokenPair rotate(String presentedToken) {
        Instant now = Instant.now();
        RefreshToken stored = refreshTokens.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (stored.isSpent()) {
            int revoked = refreshTokens.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} tokens in family {}",
                    stored.getUserId(), revoked, stored.getFamilyId());
            throw new InvalidTokenException("Refresh token reuse detected; session revoked");
        }
        if (stored.isExpired(now)) {
            throw new InvalidTokenException("Refresh token expired");
        }

        stored.markUsed(now);
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new NotFoundException("User", stored.getUserId()));

        // The refresh token records the workspace it was issued for, and the
        // membership is re-checked rather than trusted. Someone removed from a
        // workspace stops being able to refresh into it immediately, instead of
        // holding access until the access token expires.
        OrganizationMember membership = members
                .findByUserIdAndOrganizationId(user.getId(), stored.getOrganizationId())
                .filter(OrganizationMember::isActive)
                .orElseThrow(() -> new InvalidTokenException("Access to that workspace was removed"));

        return issue(user, membership, stored.getFamilyId());
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    /** Ends sessions in one workspace, leaving the person's others alone. */
    @Transactional
    public void revokeForUserInOrganization(UUID userId, UUID organizationId) {
        refreshTokens.revokeForUserInOrganization(userId, organizationId, Instant.now());
    }

    private TokenPair issue(User user, OrganizationMember membership, UUID familyId) {
        Instant now = Instant.now();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())                     // jti
                // Both are properties of the session, not the account: the same
                // person is an admin in one workspace and a member in another.
                .claim("org", membership.getOrganizationId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("roles", List.of(membership.getRole().name()))
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String rawRefresh = randomToken();
        refreshTokens.save(new RefreshToken(user.getId(), membership.getOrganizationId(),
                familyId, hash(rawRefresh), now.plus(props.refreshTokenTtl())));

        return new TokenPair(accessToken, rawRefresh, props.accessTokenTtl().toSeconds());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];                    // 256 bits of entropy
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not bcrypt. Refresh tokens are 256-bit random values, not
     * low-entropy human secrets, so there is nothing to brute-force -- and we
     * need a deterministic hash to look the row up by.
     */
    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
