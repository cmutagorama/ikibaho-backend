package com.charlie.ikibaho.platform.security;

import com.charlie.ikibaho.platform.web.ApiVersion;

import org.springframework.security.access.PermissionEvaluator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    static final String KEY_ID = "ikibaho-signing-key";

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // {bcrypt}-prefixed hashes; lets you migrate algorithms later without a big-bang reset.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(props.publicKey()).build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),            // exp / nbf
                new JwtIssuerValidator(props.issuer())); // iss must match our own issuer
        decoder.setJwtValidator(validator);

        return decoder;
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(JwtProperties props) {
        RSAKey jwk = new RSAKey.Builder(props.publicKey())
                .privateKey(props.privateKey()) // private half: signing only
                .keyID(KEY_ID)
                .build();
        return new ImmutableJWKSet<>(new JWKSet(jwk));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * With no origins configured this returns a source with no registrations, so no
     * CORS headers are emitted at all -- a misconfigured deployment fails closed.
     * <p>
     * Note: @ConditionalOnProperty cannot be used to detect the list, because a YAML
     * list binds as allowed-origins[0], [1]... and never as a bare key.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        UrlBasedCorsConfigurationSource empty = new UrlBasedCorsConfigurationSource();
        if (!props.isEnabled()) {
            return empty;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(props.exposedHeaders());
        // Required for the browser to send the httpOnly refresh cookie, and for the
        // SPA's fetch(..., { credentials: 'include' }) to be honoured.
        config.setAllowCredentials(true);
        config.setMaxAge(props.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/.well-known/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtDecoder jwtDecoder,
                                    JwtAuthenticationConverter jwtAuthConverter,
                                    ProblemAuthenticationEntryPoint entryPoint,
                                    ProblemAccessDeniedHandler deniedHandler) throws Exception {
        return http
                // Resolves the bean NAMED corsConfigurationSource. Injecting by type is
                // ambiguous: Spring MVC's mvcHandlerMappingIntrospector also implements
                // CorsConfigurationSource.
                .cors(Customizer.withDefaults())
                // Bearer tokens in the Authorization header are not sent automatically by
                // the browser, so they carry no CSRF risk. The refresh cookie IS sent
                // automatically -- it relies on SameSite=Strict plus its narrow path
                // (/api/v1/auth) instead. A successful CSRF there could only rotate the
                // victim's token, and CORS prevents the attacker from reading the response.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // public: no principal exists yet at these three
                        .requestMatchers(HttpMethod.POST, ApiVersion.V1 + "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, ApiVersion.V1 + "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, ApiVersion.V1 + "/auth/refresh").permitAll()
                        // The invitation token IS the credential; the invitee has no session yet.
                        .requestMatchers(HttpMethod.POST, ApiVersion.V1 + "/auth/accept-invitation").permitAll()
                        // public infrastructure
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .build();
    }
}
