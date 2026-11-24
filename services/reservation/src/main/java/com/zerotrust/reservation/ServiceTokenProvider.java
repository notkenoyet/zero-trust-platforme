package com.zerotrust.reservation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Token OAuth2 Client Credentials de Réservation (scope notify:send), mis en cache jusqu'à expiration. */
@Component
public class ServiceTokenProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final RestClient keycloak;
    private final String tokenUrl;
    private final String clientId;
    private final String secretFile;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(@Qualifier("keycloakClient") RestClient keycloak,
                                @Value("${app.token-url}") String tokenUrl,
                                @Value("${app.service-client-id}") String clientId,
                                @Value("${app.service-client-secret-file}") String secretFile) {
        this.keycloak = keycloak;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.secretFile = secretFile;
    }

    public synchronized String getToken() throws IOException {
        if (token != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return token;
        }
        String secret = Files.readString(Path.of(secretFile)).trim();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
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
        int expiresIn = body.get("expires_in") instanceof Number n ? n.intValue() : 60;
        this.token = accessToken;
        this.expiresAt = Instant.now().plusSeconds(expiresIn);
        return accessToken;
    }
}