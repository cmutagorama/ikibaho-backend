package com.charlie.ikibaho.identity.internal.domain;

/**
 * A person's role <em>within one organization</em>.
 *
 * Replaces GlobalRole. Under global accounts a role stored on the user would mean
 * that administering one workspace granted administration of every workspace you
 * were ever invited to -- a privilege escalation, not a convenience.
 */
public enum MemberRole {
    MEMBER,
    ADMIN
}
