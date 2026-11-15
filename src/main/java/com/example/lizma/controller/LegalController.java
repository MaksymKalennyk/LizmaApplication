package com.example.lizma.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Public legal pages (linked from the app and the App Store listing) and the contact info the app shows. */
@RestController
public class LegalController {

    @Value("${app.externalBaseUrl}")
    private String externalBaseUrl;
    @Value("${app.legal.operator-name}")
    private String operatorName;
    @Value("${app.legal.contact-email}")
    private String contactEmail;
    @Value("${app.legal.last-updated}")
    private String lastUpdated;

    @GetMapping("/legal")
    public Map<String, String> legal() {
        return Map.of(
                "contactEmail", contactEmail,
                "privacyPolicyUrl", externalBaseUrl + "/privacy",
                "termsUrl", externalBaseUrl + "/terms");
    }

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public String privacy() {
        return render("legal/privacy.html");
    }

    @GetMapping(value = "/terms", produces = MediaType.TEXT_HTML_VALUE)
    public String terms() {
        return render("legal/terms.html");
    }

    private String render(String template) {
        try {
            return new ClassPathResource(template).getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{operator}}", HtmlUtils.htmlEscape(operatorName))
                    .replace("{{contactEmail}}", HtmlUtils.htmlEscape(contactEmail))
                    .replace("{{lastUpdated}}", HtmlUtils.htmlEscape(lastUpdated));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
