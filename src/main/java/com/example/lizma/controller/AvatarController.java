package com.example.lizma.controller;

import com.example.lizma.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Profile photos, public like on any social network: the URL holds a random token that changes with
 * every new photo, so it can be cached for good and can't be guessed.
 */
@RestController
@RequiredArgsConstructor
public class AvatarController {

    private final ProfileService profileService;

    @GetMapping("/avatars/{file}")
    public ResponseEntity<byte[]> avatar(@PathVariable String file) {
        if (!file.endsWith(".jpg")) {
            return ResponseEntity.notFound().build();
        }
        return profileService.avatarImage(file.substring(0, file.length() - ".jpg".length()))
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(image))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
