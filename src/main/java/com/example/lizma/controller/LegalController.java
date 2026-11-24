package com.example.lizma.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /** The page links in the app's language: the in-app browser doesn't send the app's Accept-Language. */
    @GetMapping("/legal")
    public Map<String, String> legal(Locale locale) {
        String query = TRANSLATED.contains(locale.getLanguage()) ? "?lang=" + locale.getLanguage() : "";
        return Map.of(
                "contactEmail", contactEmail,
                "privacyPolicyUrl", externalBaseUrl + "/privacy" + query,
                "termsUrl", externalBaseUrl + "/terms" + query);
    }

    /** In the language of {@code lang}, else the browser's (Accept-Language), else English. */
    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public String privacy(@RequestParam(required = false) String lang, Locale locale) {
        return render("privacy", lang != null ? lang : locale.getLanguage());
    }

    @GetMapping(value = "/terms", produces = MediaType.TEXT_HTML_VALUE)
    public String terms(@RequestParam(required = false) String lang, Locale locale) {
        return render("terms", lang != null ? lang : locale.getLanguage());
    }

    /** Languages with their own legal pages (legal/<page>_<language>.html). */
    private static final Set<String> TRANSLATED = Set.of("uk");

    private String render(String page, String language) {
        return render(TRANSLATED.contains(language) ? "legal/" + page + "_" + language + ".html" : "legal/" + page + ".html");
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
