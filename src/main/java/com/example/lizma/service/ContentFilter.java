package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects text with words from {@code moderation/blocked-words.txt} (App Store guideline 1.2 asks for
 * a way to filter objectionable content). Entries are whole words, or roots ending with {@code *}
 * that match any word starting with them. Common letter swaps (0→o, 3→e, @→a...) are undone first.
 */
@Component
public class ContentFilter {

    public static final String MESSAGE = "Please keep it friendly: this text contains words that aren't allowed";

    private final Set<String> words = new HashSet<>();
    private final List<String> roots = new ArrayList<>();

    public ContentFilter(@Value("classpath:moderation/blocked-words.txt") Resource wordList) {
        try {
            for (String line : wordList.getContentAsString(StandardCharsets.UTF_8).split("\\R")) {
                String entry = normalize(line.strip());
                if (line.isBlank() || line.strip().startsWith("#") || entry.isBlank()) {
                    continue;
                }
                if (line.strip().endsWith("*")) {
                    roots.add(entry.strip());
                } else {
                    words.add(entry.strip());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read the blocked words list", e);
        }
    }

    /** @throws InvalidActionException (400) if the text contains a blocked word */
    public void check(String text) {
        if (containsBlockedWord(text)) {
            throw new InvalidActionException(MESSAGE);
        }
    }

    public boolean containsBlockedWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : normalize(text).split(" +")) {
            if (token.isEmpty()) {
                continue;
            }
            if (words.contains(token)) {
                return true;
            }
            for (String root : roots) {
                if (token.startsWith(root)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lower case, common letter swaps undone, everything that isn't a letter becomes a space. */
    private static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            char mapped = switch (c) {
                case '0' -> 'o';
                case '1', '!' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                case 'ё' -> 'е';
                default -> c;
            };
            out.append(Character.isLetter(mapped) ? mapped : ' ');
        }
        return out.toString();
    }
}
