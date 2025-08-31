package com.example.lizma.controller;

import com.example.lizma.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping
public class VanityLinkController {

    private final UserRepository userRepository;

    public VanityLinkController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping(value = "/{username}", produces = MediaType.TEXT_HTML_VALUE)
    public String open(@PathVariable String username) {
        if (!isUsernameCandidate(username)) {
            return notFoundHtml();
        }
        boolean exists = userRepository.existsByUsername(username);
        if (!exists) {
            return notFoundHtml();
        }
        String appUrl = "lizma://u/" + username;
        return """
            <!doctype html><html><head><meta charset='utf-8'><title>Lizma</title>
              <meta name='viewport' content='width=device-width, initial-scale=1'>
            </head><body style='font-family:-apple-system,system-ui;padding:24px'>
              <h3>Open Lizma to connect</h3>
              <p>If the app doesn't open automatically, <a href='%1$s'>tap here</a>.</p>
              <script>setTimeout(function(){ window.location.href = '%1$s'; }, 80);</script>
              <p>After opening the app and signing in, you'll be connected automatically.</p>
            </body></html>
        """.formatted(appUrl);
    }

    private static final Set<String> RESERVED = Set.of(
            "auth","user","admin","swagger-ui","swagger-resources","v3","i","actuator"
    );

    private boolean isUsernameCandidate(String u) {
        if (u == null) return false;
        if (RESERVED.contains(u)) return false;
        return u.matches("^[A-Za-z0-9_.-]{5,50}$");
    }

    private String notFoundHtml() {
        return """
            <!doctype html><html><head><meta charset='utf-8'><title>Lizma</title></head>
            <body style='font-family:-apple-system,system-ui;padding:24px'>
              <h3>User not found</h3>
              <p>The username you followed does not exist.</p>
            </body></html>
        """;
    }
}