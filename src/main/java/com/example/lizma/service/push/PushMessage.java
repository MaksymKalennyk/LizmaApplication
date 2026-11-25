package com.example.lizma.service.push;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One notification, whatever delivers it. Its text isn't written here: {@link Type#locKey} names a string
 * in the app (Localizable.xcstrings, PUSH_*) that the phone fills in with {@code args}, so everyone reads it
 * in their phone's language.
 *
 * @param threadId   groups notifications in Notification Center (one post, friends, the daily reminder)
 * @param collapseId a newer notification with the same id replaces the older one; null for none
 * @param expiresAt  when APNs stops trying to deliver it to a phone that's offline
 * @param route      where a tap leads: {@code type} plus {@code postId}, {@code commentId} or {@code username}
 */
public record PushMessage(Type type, List<String> args, String threadId, String collapseId, Instant expiresAt,
                          Map<String, Object> route) {

    public enum Type {
        FRIEND_REQUEST("PUSH_FRIEND_REQUEST", "friend_request", false),
        NEW_FRIEND("PUSH_NEW_FRIEND", "new_friend", false),
        REACTION("PUSH_REACTION", "reaction", false),
        COMMENT("PUSH_COMMENT", "comment", false),
        REPLY("PUSH_REPLY", "reply", false),
        DAILY_REMINDER("PUSH_DAILY_REMINDER", "daily_reminder", false),
        /** Silent: a friend posted, the app refreshes its home screen widget. Nothing is shown. */
        WIDGET_REFRESH(null, "widget_refresh", true);

        /** The app's string for the text; null for a silent one. */
        public final String locKey;
        /** The {@code type} the app routes a tap by. */
        public final String route;
        /** A background notification: wakes the app, shows nothing. */
        public final boolean silent;

        Type(String locKey, String route, boolean silent) {
            this.locKey = locKey;
            this.route = route;
            this.silent = silent;
        }
    }
}
