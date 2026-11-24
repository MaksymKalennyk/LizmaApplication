package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "Upload a screenshot (JPEG or PNG) for a report or feedback; send its id with it (attachmentIds)")
    @PostMapping(value = "/user/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Users me)
            throws IOException {
        return Map.of("id", attachmentService.upload(me, file.getBytes()));
    }

    @Operation(summary = "A screenshot attached to a report or feedback (moderators only)")
    @GetMapping("/admin/attachments/{file}")
    public ResponseEntity<byte[]> image(@PathVariable String file) {
        if (!file.endsWith(".jpg")) {
            return ResponseEntity.notFound().build();
        }
        return attachmentService.image(file.substring(0, file.length() - ".jpg".length()))
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        // Never stored by shared caches: it may show someone's private screen.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                        .body(image))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
