package com.example.lizma.service.push;

import com.example.lizma.model.DeviceToken;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.DeviceRegistration;
import com.example.lizma.model.dto.NotificationSettings;
import com.example.lizma.repo.DeviceTokenRepository;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** The user's phones for push notifications and what they want to be told about. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** More phones than anyone has: the ones registered longest ago go first. */
    static final int MAX_DEVICES_PER_USER = 10;

    private final DeviceTokenRepository devices;
    private final UserRepository users;

    /**
     * Remembers the phone for the user. A token someone else registered moves to this user: whoever signed
     * in on a phone last gets its notifications.
     */
    @Transactional
    public void registerDevice(Users user, DeviceRegistration registration) {
        String token = registration.token().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        DeviceToken device = devices.findByToken(token)
                .orElseGet(() -> DeviceToken.builder().token(token).createdAt(now).build());
        device.setUser(users.getReferenceById(user.getId()));
        device.setEnvironment(registration.environment() == null ? DeviceToken.Environment.PRODUCTION : registration.environment());
        device.setUpdatedAt(now);
        devices.save(device);

        List<DeviceToken> all = devices.findByUserIdOrderByUpdatedAtDesc(user.getId());
        if (all.size() > MAX_DEVICES_PER_USER) {
            devices.deleteAll(all.subList(MAX_DEVICES_PER_USER, all.size()));
        }
    }

    /** No more notifications to this phone for this user (signing out). */
    @Transactional
    public void unregisterDevice(Long userId, String token) {
        devices.deleteByTokenAndUserId(token.strip().toLowerCase(Locale.ROOT), userId);
    }

    @Transactional(readOnly = true)
    public NotificationSettings settings(Users user) {
        return NotificationSettings.of(users.findById(user.getId()).orElseThrow());
    }

    /** Changes the settings that are given; the others stay as they are. */
    @Transactional
    public NotificationSettings updateSettings(Users user, NotificationSettings change) {
        Users stored = users.findById(user.getId()).orElseThrow();
        if (change.friendRequests() != null) {
            stored.setPushFriendRequests(change.friendRequests());
        }
        if (change.reactions() != null) {
            stored.setPushReactions(change.reactions());
        }
        if (change.comments() != null) {
            stored.setPushComments(change.comments());
        }
        if (change.dailyReminder() != null) {
            stored.setPushDailyReminder(change.dailyReminder());
        }
        return NotificationSettings.of(stored);
    }
}
