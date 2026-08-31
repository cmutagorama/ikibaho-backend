package com.charlie.ikibaho.identity.internal.web;

import com.charlie.ikibaho.platform.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Reads and writes the refresh-token cookie.
 * <p>
 * httpOnly is the entire point: a token in localStorage is readable by any XSS,
 * whereas this one is unreachable from JavaScript. The rotation and reuse-detection
 * logic in TokenService is unchanged -- only how the token travels differs.
 */
@Component
class RefreshTokenCookies {

    private final RefreshCookieProperties props;
    private final JwtProperties jwt;

    RefreshTokenCookies(RefreshCookieProperties props, JwtProperties jwt) {
        this.props = props;
        this.jwt = jwt;
    }

    void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, jwt.refreshTokenTtl()).toString());
    }

    /**
     * Expire immediately; the attributes must match the original or browsers ignore it.
     */
    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> c.getName().equals(props.name()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(props.name(), value)
                .httpOnly(true)
                .secure(props.secure())
                .path(props.path())
                .sameSite(props.sameSite())
                .maxAge(maxAge)
                .build();
    }
}
