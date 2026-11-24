package com.zerotrust.reservation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String password) {}

    public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{3,30}$") String username,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String email,
        @JsonProperty("first_name") @NotBlank @Size(max = 60) String firstName,
        @JsonProperty("last_name") @NotBlank @Size(max = 60) String lastName,
        @NotBlank @Size(min = 10, max = 128) String password) {}

    private final RestClient keycloak;
    private final String tokenUrl;
    private final String usersUrl;
    private final String userClientId;
    private final String registrationSecretFile;

    public AuthController(@Qualifier("keycloakClient") RestClient keycloak,
                          @Value("${app.token-url}") String tokenUrl,
                          @Value("${app.users-url}") String usersUrl,
                          @Value("${app.user-client-id}") String userClientId,
                          @Value("${app.registration-secret-file}") String registrationSecretFile) {
        this.keycloak = keycloak;
        this.tokenUrl = tokenUrl;
        this.usersUrl = usersUrl;
        this.userClientId = userClientId;
        this.registrationSecretFile = registrationSecretFile;
    }

    /** Échange identifiant/mot de passe contre un token Keycloak, gardé dans un cookie HttpOnly (jamais lu par le JavaScript). */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", userClientId);
        form.add("username", req.username());
        form.add("password", req.password());

        Map<String, Object> body;
        try {
            body = keycloak.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP);
        } catch (HttpClientErrorException e) {
            // même message pour tous les échecs (mauvais mot de passe, compte inconnu, compte verrouillé)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants incorrects");
        } catch (RestClientException e) {
            log.warn("login: service d'identité indisponible ({})", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service d'identité indisponible");
        }
        if (body == null || !(body.get("access_token") instanceof String accessToken)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service d'identité indisponible");
        }
        int expiresIn = body.get("expires_in") instanceof Number n ? n.intValue() : 300;

        ResponseCookie cookie = ResponseCookie.from(SecurityConfig.COOKIE_NAME, accessToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofSeconds(expiresIn))
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(Map.of("username", req.username(), "expires_in", expiresIn));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        ResponseCookie cookie = ResponseCookie.from(SecurityConfig.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(Map.of("status", "logged_out"));
    }

    /** Crée un compte dans Keycloak avec un client dédié (rôle manage-users uniquement). */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        String adminToken;
        try {
            adminToken = registrationToken();
        } catch (IOException | RestClientException | IllegalStateException e) {
            log.warn("register: jeton d'inscription indisponible ({})", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service d'identité indisponible");
        }

        Map<String, Object> user = Map.of(
            "username", req.username().toLowerCase(Locale.ROOT),
            "email", req.email(),
            "firstName", req.firstName(),
            "lastName", req.lastName(),
            "enabled", true,
            "credentials", List.of(Map.of("type", "password", "value", req.password(), "temporary", false)));

        try {
            keycloak.post()
                .uri(usersUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identifiant ou e-mail déjà utilisé");
            }
            log.warn("register: refusé par Keycloak ({})", e.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inscription refusée");
        } catch (RestClientException e) {
            log.warn("register: service d'identité indisponible ({})", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service d'identité indisponible");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "created"));
    }

    private String registrationToken() throws IOException {
        String secret = Files.readString(Path.of(registrationSecretFile)).trim();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "registration-service");
        form.add("client_secret", secret);
        Map<String, Object> body = keycloak.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(MAP);
        if (body == null || !(body.get("access_token") instanceof String accessToken)) {
            throw new IllegalStateException("Réponse Keycloak invalide");
        }
        return accessToken;
    }
}