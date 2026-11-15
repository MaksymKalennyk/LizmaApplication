package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.model.UserMusicAccount;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.MusicProvider;
import com.example.lizma.repo.UserMusicAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MusicAccountService {

    /** Refresh the Spotify access token a bit before it actually expires. */
    private static final long EXPIRY_MARGIN_SECONDS = 60;

    private final UserService userService;
    private final UserMusicAccountRepository repo;
    private final SpotifyOAuthService spotifyOAuthService;

    public record Status(boolean spotifyConnected, boolean appleConnected) {}

    @Transactional(readOnly = true)
    public Status getStatus(Long userId) {
        Users user = userService.getById(userId);
        return new Status(
                repo.existsByUserAndProvider(user, MusicProvider.SPOTIFY),
                repo.existsByUserAndProvider(user, MusicProvider.APPLE));
    }

    @Transactional
    public void connectSpotify(Long userId, String accessToken, String refreshToken, Instant accessExp) {
        UserMusicAccount account = findOrCreate(userId, MusicProvider.SPOTIFY);
        account.setSpotifyAccessToken(accessToken);
        account.setSpotifyRefreshToken(refreshToken);
        account.setSpotifyAccessTokenExpiresAt(accessExp);
        repo.save(account);
    }

    @Transactional
    public void connectApple(Long userId, String userToken, String storefront) {
        UserMusicAccount account = findOrCreate(userId, MusicProvider.APPLE);
        account.setAppleUserToken(userToken);
        account.setAppleStorefront(storefront);
        repo.save(account);
    }

    /**
     * Not transactional on purpose: a refreshed token is saved right away, so a failing search afterwards
     * can't roll it back (Spotify may rotate the refresh token, losing it would force a reconnect).
     */
    public JsonNode searchSpotify(Long userId, String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidActionException("Search query must not be empty");
        }
        Users user = userService.getById(userId);
        UserMusicAccount account = repo.findByUserAndProvider(user, MusicProvider.SPOTIFY)
                .orElseThrow(() -> new ConflictException("Spotify account is not connected"));

        return spotifyOAuthService.searchTracks(query.strip(), validAccessToken(account));
    }

    private String validAccessToken(UserMusicAccount account) {
        Instant expiresAt = account.getSpotifyAccessTokenExpiresAt();
        boolean stillValid = account.getSpotifyAccessToken() != null && expiresAt != null
                && Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_MARGIN_SECONDS));
        if (stillValid) {
            return account.getSpotifyAccessToken();
        }
        if (account.getSpotifyRefreshToken() == null) {
            throw new ConflictException("Spotify session expired, please reconnect Spotify");
        }

        SpotifyOAuthService.ExchangeResult refreshed = spotifyOAuthService.refreshAccessToken(account.getSpotifyRefreshToken());
        account.setSpotifyAccessToken(refreshed.accessToken());
        account.setSpotifyRefreshToken(refreshed.refreshToken());
        account.setSpotifyAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
        repo.save(account);
        return refreshed.accessToken();
    }

    private UserMusicAccount findOrCreate(Long userId, MusicProvider provider) {
        Users user = userService.getById(userId);
        return repo.findByUserAndProvider(user, provider)
                .orElseGet(() -> UserMusicAccount.builder().user(user).provider(provider).build());
    }
}
