package com.zerotrust.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    public record NotificationRequest(
        @JsonProperty("reservation_id") @NotBlank @Size(max = 100) String reservationId,
        @NotBlank @Size(max = 100) String user,
        @NotBlank @Size(max = 100) String room,
        @NotBlank @Size(max = 100) String date) {}

    public record NotificationResponse(
        String status,
        @JsonProperty("notification_id") String notificationId,
        @JsonProperty("reservation_id") String reservationId,
        String recipient,
        String message,
        @JsonProperty("sent_at") String sentAt) {}

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("state", "ok", "service", "notification");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "service", "notification");
    }

    /** Scope notify:send ET client autorisé (azp) : sinon 403. */
    @PostMapping("/notify")
    @PreAuthorize("hasAuthority('SCOPE_notify:send') and @clients.allowed(authentication)")
    public NotificationResponse send(@Valid @RequestBody NotificationRequest req) {
        String id = UUID.randomUUID().toString();
        String message = "Bonjour %s, votre réservation de la salle %s le %s est confirmée."
            .formatted(req.user(), req.room(), req.date());
        log.info("NOTIFICATION RECUE id={} reservation={} user={}",
            id, clean(req.reservationId()), clean(req.user()));
        return new NotificationResponse("sent", id, req.reservationId(), req.user(), message,
            Instant.now().toString());
    }

    // Empêche l'injection de fausses lignes dans les journaux
    private static String clean(String value) {
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}