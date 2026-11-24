package com.zerotrust.reservation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);
    private static final String USER_ACCESS = "hasAuthority('SCOPE_reservations:use') and @clients.allowed(authentication)";

    public record ReservationRequest(
        @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String room,
        @NotBlank @Size(max = 100) String date) {}

    public record ReservationRecord(
        String id, String name, String room, String date, String user,
        @JsonProperty("notification_status") String notificationStatus) {}

    private final NotificationClient notifications;
    private final List<ReservationRecord> store = new CopyOnWriteArrayList<>();

    public ReservationController(NotificationClient notifications) {
        this.notifications = notifications;
    }

    private static String usernameOf(Jwt jwt) {
        String name = jwt.getClaimAsString("preferred_username");
        return name != null ? name : jwt.getSubject();
    }

    @GetMapping("/me")
    @PreAuthorize(USER_ACCESS)
    public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("username", usernameOf(jwt));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(USER_ACCESS)
    public Map<String, Object> create(@Valid @RequestBody ReservationRequest req,
                                      @AuthenticationPrincipal Jwt jwt) {
        String username = usernameOf(jwt);   // propriétaire : issu du token, jamais du corps de la requête
        String name = (req.name() == null || req.name().isBlank()) ? username : req.name();
        String id = UUID.randomUUID().toString();

        Map<String, Object> notification;
        String status;
        String message;
        try {
            notification = notifications.send(id, name, req.room(), req.date());
            status = "sent";
            message = "Réservation confirmée";
        } catch (Exception e) {
            log.warn("notification failed: {}", e.getClass().getSimpleName());
            notification = Map.of("status", "failed",
                "message", "La réservation est enregistrée mais la notification n'a pas pu être envoyée.");
            status = "failed";
            message = "Réservation créée, notification non envoyée";
        }

        ReservationRecord record = new ReservationRecord(id, name, req.room(), req.date(), username, status);
        store.add(record);
        return Map.of("message", message, "reservation", record, "notification", notification);
    }

    @GetMapping("/reservations")
    @PreAuthorize(USER_ACCESS)
    public Map<String, Object> list(@AuthenticationPrincipal Jwt jwt) {
        String username = usernameOf(jwt);
        return Map.of("reservations", store.stream().filter(r -> r.user().equals(username)).toList());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "service", "reservation");
    }
}