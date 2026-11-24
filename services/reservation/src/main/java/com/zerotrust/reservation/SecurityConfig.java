package com.zerotrust.reservation;

import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String COOKIE_NAME = "__Host-session";
    static final String USER_SCOPE = "SCOPE_reservations:use";

    /** Chemins sans authentification (pas de token lu : un cookie périmé ne bloque pas la connexion). */
    private static boolean isPublic(String path) {
        return path.equals("/health") || path.equals("/login") || path.equals("/register")
            || path.equals("/logout") || path.equals("/app/login") || path.equals("/app/register")
            || path.startsWith("/static/");
    }

    /** Token : en-tête Authorization (API) ou cookie de session (navigateur). */
    private static String resolveToken(HttpServletRequest request) {
        if (isPublic(request.getRequestURI())) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Pages : sans session valide, redirection vers la connexion. API : 401.
        AuthenticationEntryPoint toLogin = (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", "/app/login");
        };
        RequestMatcher pageRequest = request -> {
            String p = request.getRequestURI();
            return p.equals("/") || p.equals("/app") || p.startsWith("/app/");
        };
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(pageRequest, toLogin);
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new BearerTokenAuthenticationEntryPoint());

        BearerTokenResolver resolver = SecurityConfig::resolveToken;

        http
            // Cookie SameSite=Strict + API en JSON uniquement : pas de jeton CSRF séparé
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.contentSecurityPolicy(
                csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'")))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .requestMatchers("/static/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/app/login", "/app/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/login", "/register", "/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/app/reservation", "/app/mes-reservations")
                    .hasAuthority(USER_SCOPE)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(resolver)
                .authenticationEntryPoint(entryPoint)
                .jwt(jwt -> { }));
        return http.build();
    }

    /** Signature RS256 uniquement, puis iss, exp et aud. Échec = 401. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.oidc.jwks-url}") String jwksUrl,
                          @Value("${app.oidc.issuer}") String issuer,
                          @Value("${app.oidc.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();

        OAuth2TokenValidator<Jwt> requireExp = jwt -> jwt.getExpiresAt() != null
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "exp manquant", null));

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience invalide", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), requireExp, audienceValidator));
        return decoder;
    }
}