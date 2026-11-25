package com.example.lizma.service.push;

import com.example.lizma.model.DeviceToken;
import com.example.lizma.model.Post;
import com.example.lizma.model.PostComment;
import com.example.lizma.model.Users;
import com.example.lizma.repo.DeviceTokenRepository;
import com.example.lizma.repo.PostCommentRepository;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.repo.UserRepository;
import com.example.lizma.service.ActivityEvent;
import com.example.lizma.service.BlockService;
import com.example.lizma.service.FriendshipService;
import com.example.lizma.service.PostCreatedEvent;
import com.example.lizma.service.WidgetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.lizma.service.push.PushMessage.Type.*;

/**
 * Decides who gets a push notification about what, and sends it: friend requests and new friends, the first
 * reaction to a post, comments and replies (each as the recipient chose in Profile -> Notifications, never
 * about yourself, never between users who blocked each other), and the daily reminder at 13:00 Kyiv time
 * for everyone who hasn't posted that day.
 * <p>
 * Runs after the action is committed, off the request thread. Phones APNs no longer knows are forgotten.
 */
@Slf4j
@Service
public class PushNotifier {

    /** Longest comment text in a notification. */
    static final int MAX_TEXT = 120;
    private static final int MAX_TRACK = 80;
    /** The same friend request or reaction again (cancel and resend, remove and react again) is news once a day. */
    private static final Duration REPEAT_QUIET = Duration.ofHours(12);
    private static final Duration SOCIAL_EXPIRY = Duration.ofDays(1);
    /** The reminder is about today: a phone that's offline until evening doesn't get it any more. */
    private static final Duration REMINDER_EXPIRY = Duration.ofHours(5);
    /** A widget counts as on someone's screen if it fetched this recently. */
    static final Duration WIDGET_ACTIVE = Duration.ofDays(3);
    /** At most one silent widget refresh per phone in this time: iOS allows only a few background pushes an hour. */
    static final Duration WIDGET_QUIET = Duration.ofMinutes(15);

    private final DeviceTokenRepository devices;
    private final UserRepository users;
    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final BlockService blocks;
    private final FriendshipService friendships;
    private final WidgetService widgets;
    private final PushGateway gateway;
    private final TransactionTemplate transactions;
    private final ZoneId reminderZone;
    private final Map<String, Instant> recentlySent = new ConcurrentHashMap<>();

    public PushNotifier(DeviceTokenRepository devices, UserRepository users, PostRepository posts,
                        PostCommentRepository comments, BlockService blocks, FriendshipService friendships,
                        WidgetService widgets, PushGateway gateway, PlatformTransactionManager transactionManager,
                        @Value("${app.push.daily-reminder-zone:Europe/Kyiv}") String reminderZone) {
        this.devices = devices;
        this.users = users;
        this.posts = posts;
        this.comments = comments;
        this.blocks = blocks;
        this.friendships = friendships;
        this.widgets = widgets;
        this.gateway = gateway;
        // Its own transactions: after a commit the finished one is still bound to the thread.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reminderZone = ZoneId.of(reminderZone);
    }

    /** A phone to deliver to, read inside a transaction and used after it. */
    record Target(Long userId, String token, DeviceToken.Environment environment) {
        static Target of(DeviceToken device) {
            return new Target(device.getUser().getId(), device.getToken(), device.getEnvironment());
        }
    }

    private record Delivery(List<Target> targets, PushMessage message) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivity(ActivityEvent event) {
        List<Delivery> deliveries = transactions.execute(status -> plan(event));
        if (deliveries != null) {
            deliveries.forEach(delivery -> delivery.targets().forEach(target -> deliver(target, delivery.message())));
        }
    }

    /**
     * A friend posted: a silent push wakes the app of everyone whose widget is on a screen, so it shows the new song
     * soon instead of at its next scheduled refresh. Phones that got one in the last 15 minutes are skipped.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        List<Target> targets = transactions.execute(status -> widgetRefreshTargets(event.authorId()));
        if (targets == null || targets.isEmpty()) {
            return;
        }
        PushMessage message = new PushMessage(WIDGET_REFRESH, List.of(), null, null,
                Instant.now().plus(Duration.ofHours(1)), Map.of("type", WIDGET_REFRESH.route));
        targets.stream()
                .filter(target -> !sentWithin("widget:" + target.token(), WIDGET_QUIET))
                .forEach(target -> deliver(target, message));
    }

    private List<Target> widgetRefreshTargets(Long authorId) {
        Users author = users.findById(authorId).orElse(null);
        if (author == null || author.isBanned()) {
            return List.of();
        }
        List<Long> withWidget = widgets.usersWithWidget(friendships.getFriendIds(author), WIDGET_ACTIVE);
        return withWidget.isEmpty() ? List.of() : devices.findByUserIds(withWidget).stream().map(Target::of).toList();
    }

    @Scheduled(cron = "${app.push.daily-reminder-cron:0 0 13 * * *}", zone = "${app.push.daily-reminder-zone:Europe/Kyiv}")
    public void sendDailyReminders() {
        Instant startOfToday = LocalDate.now(reminderZone).atStartOfDay(reminderZone).toInstant();
        int phones = remindEveryoneWhoHasntPostedSince(startOfToday);
        log.info("Daily reminder sent to {} phone(s)", phones);
    }

    /** "What are you listening to today?" to every phone whose user wants it and hasn't posted since {@code since}. */
    public int remindEveryoneWhoHasntPostedSince(Instant since) {
        List<Target> targets = transactions.execute(status ->
                devices.findDueDailyReminder(since).stream().map(Target::of).toList());
        if (targets == null) {
            return 0;
        }
        PushMessage message = new PushMessage(DAILY_REMINDER, List.of(), "daily", "daily-reminder",
                Instant.now().plus(REMINDER_EXPIRY), Map.of("type", DAILY_REMINDER.route));
        targets.forEach(target -> deliver(target, message));
        return targets.size();
    }

    private void deliver(Target target, PushMessage message) {
        PushGateway.Result result = gateway.send(target.environment(), target.token(), message);
        if (result == PushGateway.Result.INVALID_TOKEN) {
            transactions.executeWithoutResult(status -> devices.deleteByToken(target.token()));
            log.info("Forgot a phone of user #{}: APNs no longer takes its token", target.userId());
        }
    }

    private List<Delivery> plan(ActivityEvent event) {
        return switch (event) {
            case ActivityEvent.FriendRequestSent sent -> friendNews(sent.requesterId(), sent.recipientId(), FRIEND_REQUEST);
            case ActivityEvent.BecameFriends friends -> friendNews(friends.actorId(), friends.recipientId(), NEW_FRIEND);
            case ActivityEvent.PostReacted reacted -> reaction(reacted);
            case ActivityEvent.CommentAdded added -> comment(added);
        };
    }

    private List<Delivery> friendNews(Long actorId, Long recipientId, PushMessage.Type type) {
        Users actor = users.findById(actorId).orElse(null);
        Users recipient = users.findById(recipientId).orElse(null);
        if (!mayNotify(actor, recipient) || !recipient.isPushFriendRequests()
                || (type == FRIEND_REQUEST && sentRecently(type + ":" + actorId + ":" + recipientId))) {
            return List.of();
        }
        return deliveries(recipient, new PushMessage(type, List.of(nameOf(actor)), "friends", null,
                Instant.now().plus(SOCIAL_EXPIRY), Map.of("type", type.route, "username", actor.getUsername())));
    }

    private List<Delivery> reaction(ActivityEvent.PostReacted reacted) {
        Post post = posts.findById(reacted.postId()).orElse(null);
        Users actor = users.findById(reacted.reactorId()).orElse(null);
        if (post == null) {
            return List.of();
        }
        Users author = post.getAuthor();
        if (!mayNotify(actor, author) || !author.isPushReactions()
                || sentRecently(REACTION + ":" + actor.getId() + ":" + post.getId())) {
            return List.of();
        }
        return deliveries(author, new PushMessage(REACTION,
                List.of(nameOf(actor), reacted.reaction(), shorten(post.getTrackName(), MAX_TRACK)),
                "post-" + post.getId(), null, Instant.now().plus(SOCIAL_EXPIRY),
                Map.of("type", REACTION.route, "postId", post.getId())));
    }

    /** A reply goes to whoever wrote the comment it answers; the post's author hears about any comment. */
    private List<Delivery> comment(ActivityEvent.CommentAdded added) {
        PostComment comment = comments.findById(added.commentId()).orElse(null);
        if (comment == null) {
            return List.of();
        }
        Users actor = comment.getAuthor();
        Post post = comment.getPost();
        List<String> args = List.of(nameOf(actor), shorten(comment.getContent(), MAX_TEXT));
        Map<String, Object> route = Map.of("postId", post.getId(), "commentId", comment.getId());
        List<Delivery> result = new ArrayList<>();

        Users repliedTo = comment.getParentComment() == null ? null : comment.getParentComment().getAuthor();
        if (mayNotify(actor, repliedTo) && repliedTo.isPushComments()) {
            result.addAll(deliveries(repliedTo, message(REPLY, args, post, route)));
        }
        Users postAuthor = post.getAuthor();
        boolean alreadyTold = repliedTo != null && repliedTo.getId().equals(postAuthor.getId());
        if (!alreadyTold && mayNotify(actor, postAuthor) && postAuthor.isPushComments()) {
            result.addAll(deliveries(postAuthor, message(COMMENT, args, post, route)));
        }
        return result;
    }

    private static PushMessage message(PushMessage.Type type, List<String> args, Post post, Map<String, Object> route) {
        Map<String, Object> withType = new java.util.HashMap<>(route);
        withType.put("type", type.route);
        return new PushMessage(type, args, "post-" + post.getId(), null, Instant.now().plus(SOCIAL_EXPIRY), withType);
    }

    private List<Delivery> deliveries(Users recipient, PushMessage message) {
        List<Target> targets = devices.findByUserIdOrderByUpdatedAtDesc(recipient.getId()).stream().map(Target::of).toList();
        return targets.isEmpty() ? List.of() : List.of(new Delivery(targets, message));
    }

    /** Someone else, both still here, neither suspended, no block between them. */
    private boolean mayNotify(Users actor, Users recipient) {
        return actor != null && recipient != null && !actor.getId().equals(recipient.getId())
                && !actor.isBanned() && !recipient.isBanned()
                && !blocks.blockedEitherWay(actor.getId(), recipient.getId());
    }

    /** True if the same news went out lately; otherwise remembers it as sent now. */
    private boolean sentRecently(String key) {
        return sentWithin(key, REPEAT_QUIET);
    }

    /** True if something with this key went out within {@code quiet}; otherwise remembers it as sent now. */
    private boolean sentWithin(String key, Duration quiet) {
        Instant now = Instant.now();
        if (recentlySent.size() > 10_000) {
            recentlySent.values().removeIf(sentAt -> sentAt.isBefore(now.minus(REPEAT_QUIET)));
        }
        Instant previous = recentlySent.get(key);
        if (previous != null && previous.isAfter(now.minus(quiet))) {
            return true;
        }
        recentlySent.put(key, now);
        return false;
    }

    private static String nameOf(Users user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank() ? user.getDisplayName() : user.getUsername();
    }

    /** One line, at most {@code max} characters (an emoji is never cut in half). */
    static String shorten(String text, int max) {
        String line = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (line.codePointCount(0, line.length()) <= max) {
            return line;
        }
        return line.substring(0, line.offsetByCodePoints(0, max - 1)).stripTrailing() + "…";
    }
}
