package com.example.lizma.service.push;

import com.example.lizma.model.DeviceToken;

/** Delivers a notification to one phone: APNs, or only the log while APNs isn't set up. */
public interface PushGateway {

    enum Result {
        SENT,
        /** The phone no longer takes notifications from the app (deleted it, or a bad token): forget the token. */
        INVALID_TOKEN,
        /** Didn't go out this time (network, APNs busy, configuration); nothing to change. */
        FAILED
    }

    Result send(DeviceToken.Environment environment, String deviceToken, PushMessage message);
}
