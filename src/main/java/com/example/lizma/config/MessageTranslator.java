package com.example.lizma.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the messages the API sends to the app ({@code {"message": "..."}}) into the language the app
 * asks for (Accept-Language). The code keeps writing English; {@code i18n/<language>.json} maps each English
 * message to its translation. Messages with a varying part use {@code {0}}, {@code {1}} in both texts.
 * Anything without a translation stays English.
 */
@Component
public class MessageTranslator {

    /** Languages with a translation file in src/main/resources/i18n. */
    static final List<String> LANGUAGES = List.of("uk");

    private record Template(Pattern english, String translated) {
    }

    private final Map<String, Map<String, String>> exact = new HashMap<>();
    private final Map<String, List<Template>> templates = new HashMap<>();

    public MessageTranslator(ObjectMapper objectMapper) {
        for (String language : LANGUAGES) {
            Map<String, String> table;
            try (InputStream in = new ClassPathResource("i18n/" + language + ".json").getInputStream()) {
                table = objectMapper.readValue(in, new TypeReference<Map<String, String>>() { });
            } catch (IOException e) {
                throw new UncheckedIOException("Can't read the " + language + " translations", e);
            }
            Map<String, String> plain = new HashMap<>();
            List<Template> withArguments = new ArrayList<>();
            table.forEach((english, translated) -> {
                if (english.contains("{0}")) {
                    withArguments.add(new Template(toPattern(english), translated));
                } else {
                    plain.put(english, translated);
                }
            });
            exact.put(language, plain);
            templates.put(language, withArguments);
        }
    }

    public String translate(String message, Locale locale) {
        if (message == null || locale == null) {
            return message;
        }
        String language = locale.getLanguage();
        Map<String, String> table = exact.get(language);
        if (table == null) {
            return message;
        }
        String translated = table.get(message);
        if (translated != null) {
            return translated;
        }
        for (Template template : templates.get(language)) {
            Matcher matcher = template.english().matcher(message);
            if (matcher.matches()) {
                String result = template.translated();
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    // A varying part may itself be a known phrase.
                    String argument = matcher.group(i);
                    result = result.replace("{" + (i - 1) + "}", table.getOrDefault(argument, argument));
                }
                return result;
            }
        }
        return message;
    }

    /** "Try again in {0} minutes." → a pattern that captures what stands at {0}. */
    private static Pattern toPattern(String english) {
        StringBuilder regex = new StringBuilder();
        Matcher placeholder = Pattern.compile("\\{\\d+}").matcher(english);
        int last = 0;
        while (placeholder.find()) {
            regex.append(Pattern.quote(english.substring(last, placeholder.start()))).append("(.+?)");
            last = placeholder.end();
        }
        regex.append(Pattern.quote(english.substring(last)));
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    /** True when the language has its own texts (en needs nothing). */
    public static boolean translates(Locale locale) {
        return locale != null && LANGUAGES.contains(locale.getLanguage());
    }
}
