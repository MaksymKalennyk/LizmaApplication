package com.example.lizma.model.dto;

/**
 * A song found from a shared link, ready to post: Apple's catalog entry (its id, cover, 30-second preview and
 * Apple Music page) and, for a link from Spotify, that exact Spotify track.
 */
public record SharedTrack(String musicId,
                          String trackName,
                          String artistName,
                          String albumName,
                          String albumCoverUrl,
                          String previewUrl,
                          String appleMusicUrl,
                          String spotifyUrl) {
}
