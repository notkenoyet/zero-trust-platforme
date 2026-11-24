package com.zerotrust.reservation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Seuls les clients OAuth2 listés (claim azp) peuvent utiliser l'API : ici la page web. */
@Component("clients")
public class ClientPolicy {

    private final Set<String> allowed;

    public ClientPolicy(@Value("${app.oidc.allowed-client-ids}") String allowedClientIds) {
        this.allowed = Arrays.stream(allowedClientIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allowed(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token
            && allowed.contains(token.getToken().getClaimAsString("azp"));
    }
}