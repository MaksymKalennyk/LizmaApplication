package com.example.lizma.model.dto;

import com.example.lizma.model.Users;

/**
 * What the user wants push notifications about. In a change, a missing (null) field stays as it is.
 */
public record NotificationSettings(Boolean friendRequests, Boolean reactions, Boolean comments, Boolean dailyReminder) {

    public static NotificationSettings of(Users user) {
        return new NotificationSettings(user.isPushFriendRequests(), user.isPushReactions(),
                user.isPushComments(), user.isPushDailyReminder());
    }
}
