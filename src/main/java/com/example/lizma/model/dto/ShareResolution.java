package com.example.lizma.model.dto;

import java.util.List;

/**
 * What a shared link turned out to be. {@code matched}: {@code tracks} holds the one song to post. Otherwise it holds
 * the likeliest songs for the user to choose from (a Spotify link whose song couldn't be told apart for sure).
 */
public record ShareResolution(boolean matched, List<SharedTrack> tracks) {

    public static ShareResolution exact(SharedTrack track) {
        return new ShareResolution(true, List.of(track));
    }

    public static ShareResolution choices(List<SharedTrack> tracks) {
        return new ShareResolution(false, List.copyOf(tracks));
    }
}
