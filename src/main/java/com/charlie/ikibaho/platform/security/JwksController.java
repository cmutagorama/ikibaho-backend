package com.charlie.ikibaho.platform.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

public class JwksController {
    private final Map<String, Object> jwks;

    JwksController(JwtProperties props) {
        // Built from the PUBLIC key only, so the private half cannot leak here even by accident.
        RSAKey key = new RSAKey.Builder(props.publicKey()).keyID(SecurityConfig.KEY_ID).build();
        this.jwks = new JWKSet(key).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return jwks;
    }
}
