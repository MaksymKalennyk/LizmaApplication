package com.example.lizma.service.push;

import com.example.lizma.model.DeviceToken;
import lombok.extern.slf4j.Slf4j;

/**
 * Used while APNs isn't set up (no key from the paid Apple Developer account yet): writes to the log what
 * would have been sent, without names or comment text.
 */
@Slf4j
public class LogOnlyPushGateway implements PushGateway {

    @Override
    public Result send(DeviceToken.Environment environment, String deviceToken, PushMessage message) {
        log.info("Push not sent, APNs isn't set up: {} {} to {} device …{}", message.type(), message.route(),
                environment, deviceToken.substring(Math.max(0, deviceToken.length() - 6)));
        return Result.SENT;
    }
}
