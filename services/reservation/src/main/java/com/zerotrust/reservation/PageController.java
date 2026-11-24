package com.zerotrust.reservation;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Pages HTML servies uniquement après le contrôle de sécurité (voir SecurityConfig). */
@Controller
public class PageController {

    @GetMapping({"/", "/app", "/app/"})
    public ResponseEntity<Void> home() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/app/reservation")).build();
    }

    @GetMapping("/app/login")
    public ResponseEntity<Resource> login() {
        return page("login.html");
    }

    @GetMapping("/app/register")
    public ResponseEntity<Resource> register() {
        return page("register.html");
    }

    @GetMapping("/app/reservation")
    public ResponseEntity<Resource> reservation() {
        return page("reservation.html");
    }

    @GetMapping("/app/mes-reservations")
    public ResponseEntity<Resource> mine() {
        return page("mes-reservations.html");
    }

    private static ResponseEntity<Resource> page(String name) {
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(new ClassPathResource("pages/" + name));
    }
}