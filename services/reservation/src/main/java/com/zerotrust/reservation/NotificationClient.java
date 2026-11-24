package com.zerotrust.reservation;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public NotificationClient(@Qualifier("notificationRestClient") RestClient client, ServiceTokenProvider tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    /** Appel mTLS + token Bearer vers Notification. Lève une exception si l'appel échoue. */
    public Map<String, Object> send(String reservationId, String user, String room, String date) throws IOException {
        return client.post()
            .uri("/notify")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getToken())
            .body(Map.of("reservation_id", reservationId, "user", user, "room", room, "date", date))
            .retrieve()
            .body(MAP);
    }
}