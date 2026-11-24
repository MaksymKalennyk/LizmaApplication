package com.example.lizma.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** What counts as one emoji (the rule for reactions). */
class ReactionEmojiTest {

    @ParameterizedTest
    @ValueSource(strings = {"❤️", "❤", "🔥", "👍🏽", "👨‍👩‍👧‍👦", "🏳️‍🌈", "🧑🏽‍💻", "😶‍🌫️", "❤️‍🔥", "👩🏻‍❤️‍💋‍👨🏼",
            "🇺🇦", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "1️⃣", "#️⃣", "*️⃣", "☺️", "©️", "🫨", "🫩", "🪿"})
    void oneEmojiOfAnyKind(String emoji) {
        assertThat(PostService.isSingleEmoji(emoji)).as(emoji).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ok", "1", "#", ":)", "🔥🔥", "a🔥", "🔥a", "🇺🇦🇺🇦", "👍 "})
    void notOneEmoji(String text) {
        assertThat(PostService.isSingleEmoji(text)).as(text).isFalse();
    }
}
