package com.example.lizma.service;

import com.example.lizma.model.UserMusicAccount;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.MusicProvider;
import com.example.lizma.repo.UserMusicAccountRepository;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MusicAccountService {
    private final UserRepository userRepository;
    private final UserMusicAccountRepository repo;
    private final SpotifyOAuthService spotifyOAuthService;

    public record Status(boolean spotifyConnected, boolean appleConnected) {}

    public Status getStatus(Long userId) {
        Users u = userRepository.findById(userId).orElseThrow();
        boolean sp = repo.existsByUserAndProvider(u, MusicProvider.SPOTIFY);
        boolean am = repo.existsByUserAndProvider(u, MusicProvider.APPLE);
        return new Status(sp, am);
    }

    public void connectSpotify(Long userId, String accessToken, String refreshToken, Instant accessExp) {
        Users u = userRepository.findById(userId).orElseThrow();
        UserMusicAccount acc = repo.findByUserAndProvider(u, MusicProvider.SPOTIFY)
                .orElse(UserMusicAccount.builder().user(u).provider(MusicProvider.SPOTIFY).build());
        acc.setSpotifyAccessToken(accessToken);
        acc.setSpotifyRefreshToken(refreshToken);
        acc.setSpotifyAccessTokenExpiresAt(accessExp);
        repo.save(acc);
    }

    public void connectApple(Long userId, String userToken, String storefront) {
        Users u = userRepository.findById(userId).orElseThrow();
        UserMusicAccount acc = repo.findByUserAndProvider(u, MusicProvider.APPLE)
                .orElse(UserMusicAccount.builder().user(u).provider(MusicProvider.APPLE).build());
        acc.setAppleUserToken(userToken);
        acc.setAppleStorefront(storefront);
        repo.save(acc);
    }

    public com.fasterxml.jackson.databind.JsonNode searchSpotify(Long userId, String query) {
        Users u = userRepository.findById(userId).orElseThrow();
        UserMusicAccount acc = repo.findByUserAndProvider(u, MusicProvider.SPOTIFY)
                .orElseThrow(() -> new RuntimeException("Spotify account not connected"));
                
        String accessToken = validateAndRefreshSpotifyToken(acc);
        return spotifyOAuthService.searchTracks(query, accessToken); 
    }

    private String validateAndRefreshSpotifyToken(UserMusicAccount acc) {
        if (acc.getSpotifyAccessToken() == null || acc.getSpotifyAccessTokenExpiresAt() == null || Instant.now().isAfter(acc.getSpotifyAccessTokenExpiresAt().minusSeconds(60))) {
            SpotifyOAuthService.ExchangeResult res = spotifyOAuthService.refreshAccessToken(acc.getSpotifyRefreshToken());
            acc.setSpotifyAccessToken(res.accessToken());
            acc.setSpotifyRefreshToken(res.refreshToken());
            acc.setSpotifyAccessTokenExpiresAt(res.accessTokenExpiresAt());
            repo.save(acc);
            return res.accessToken();
        }
        return acc.getSpotifyAccessToken();
    }
}