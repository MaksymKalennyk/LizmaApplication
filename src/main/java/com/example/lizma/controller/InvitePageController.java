package com.example.lizma.controller;

import com.example.lizma.service.FriendInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Public page behind an invite link: opens the app with {@code lizma://i/<token>}. */
@RestController
@RequiredArgsConstructor
public class InvitePageController {

    private final FriendInviteService inviteService;

    @GetMapping(value = "${app.invites.pathPrefix:/i}/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> open(@PathVariable String token) {
        return inviteService.usableInviteOwner(token)
                .map(inviter -> ResponseEntity.ok(invitePage(token, inviter)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(invalidPage()));
    }

    /** {@code token} is a stored invite token (base64url), so it is safe to embed. */
    private static String invitePage(String token, String inviter) {
        String appUrl = "lizma://i/" + token;
        return """
            <!doctype html><html><head><meta charset='utf-8'><title>Lizma invite</title>
              <meta name='viewport' content='width=device-width, initial-scale=1'>
            </head><body style='font-family:-apple-system,system-ui;padding:24px'>
              <h3>%2$s invited you to Lizma</h3>
              <p>If the app doesn't open automatically, <a href='%1$s'>tap here</a>.</p>
              <script>setTimeout(function(){ window.location.href = '%1$s'; }, 80);</script>
              <p>After opening the app and signing in, you'll become friends automatically.</p>
            </body></html>
        """.formatted(appUrl, HtmlUtils.htmlEscape(inviter));
    }

    private static String invalidPage() {
        return """
            <!doctype html><html><head><meta charset='utf-8'><title>Lizma invite</title>
              <meta name='viewport' content='width=device-width, initial-scale=1'>
            </head><body style='font-family:-apple-system,system-ui;padding:24px'>
              <h3>This invite link is no longer valid</h3>
              <p>It was already used or has expired. Ask your friend to send a new one.</p>
            </body></html>
        """;
    }
}
