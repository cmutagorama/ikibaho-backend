package com.charlie.ikibaho.platform.web;

/**
 * The single place the API's version prefix is spelled.
 *
 * Controllers concatenate rather than hardcode so that introducing a v2 is a
 * change to this file plus the controllers that actually move -- not a
 * find-and-replace across every mapping.
 */
public final class ApiVersion {

    public static final String V1 = "/api/v1";

    private ApiVersion() {
    }
}
