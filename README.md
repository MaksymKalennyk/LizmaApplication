# Lizma

Social music app: a Spring Boot backend (this folder) and a SwiftUI iOS client (`lizma/`).
Users add friends, share songs to a friends-only feed, react with emoji and comment. Songs come from Apple's catalog,
so anyone can post without a music account; a post plays a 30-second preview and opens in Apple Music or Spotify.
Profiles have a name and a photo; push notifications tell people about friend requests, reactions and comments.

## Requirements

- JDK 21 (IntelliJ's bundled JBR 21 is fine)
- Docker Desktop (local MySQL)
- Xcode 26+ for the iOS app

## Run the backend

1. Create local settings: `cp .env.example .env`, set the passwords and generate a JWT key with
   `openssl rand -base64 32` for `SECRET_KEY`. `.env` is git-ignored.
2. Start MySQL: `docker compose up -d` — MySQL 8 on `localhost:3307`
   (container `lizma-mysql`, data kept in the `lizma_mysql-data` volume).
3. Start the server, either:
   - **IntelliJ IDEA**: open the project, reload Gradle, open `LizmaApplication.java` and press ▶ next to `main`.
     No environment variables are needed: `application.properties` imports `.env` from the project root.
   - **Terminal**: `./gradlew bootRun`
4. API on http://localhost:8080. Swagger UI: http://localhost:8080/swagger-ui.html
   (sign in via `POST /auth/sign-in`, press **Authorize** and paste the `token`).

### Database migrations

The schema comes from Flyway migrations in `src/main/resources/db/migration` (`V1__baseline.sql`, `V2__…`); Hibernate only
checks it (`ddl-auto=validate`). A database created before migrations existed is taken as being at V1 on the first start.
Every schema change is a new `V<n>__<what>.sql`; never edit one that has run. `MigrationTest` runs all of them on a real
MySQL in Docker and also checks that every Java enum value fits its MySQL `ENUM` column.

### Sessions

Sign-in returns a short-lived access token (`token`, 15 min) and a `refreshToken` (30 days, single use).
`POST /auth/refresh {"refreshToken": …}` returns a new pair; using a refresh token twice ends that session (it was copied).
`POST /auth/logout` ends the session. Suspending or deleting an account ends all of its sessions.
Sign-in is limited to 5 wrong passwords per username and 30 per IP address in 15 minutes, sign-up to 10 per IP and hour
(429 with a "try again in N minutes" message). The limits live in memory, per server.

## Tests

`./gradlew test` runs the API integration tests on an in-memory H2 database; `MigrationTest` additionally needs Docker
(it is skipped without it). The iOS unit tests (session renewal against a mock server, helpers) run with
`xcodebuild test -project lizma/lizma.xcodeproj -scheme lizma -only-testing:lizmaTests -destination 'platform=iOS Simulator,name=iPhone 17 Pro'`
or ⌘U in Xcode.

## iOS app

Open `lizma/lizma.xcodeproj`, choose the `lizma` scheme and an iPhone simulator, run.
The app calls `http://localhost:8080` (`lizma/lizma/Config/AppConfig.swift`). A debug build takes another address
from the `LIZMA_API_BASE_URL` environment variable (Xcode: Edit Scheme → Run → Arguments), e.g. your Mac's IP for a real device.

Languages: English and Ukrainian (`lizma/lizma/Localizable.xcstrings`, `InfoPlist.xcstrings`). New strings show up in the
catalog when Xcode builds; translate them there. A command-line build doesn't update the catalog, `xcodebuild
-exportLocalizations -project lizma/lizma.xcodeproj -localizationPath <dir> -exportLanguage uk` does. The texts of push
notifications are the `PUSH_*` keys (added by hand, the server sends only the key and its arguments).

The language can be changed in the app (Profile → Language: the phone's, English or Ukrainian). The choice is the app's
own `AppleLanguages` setting, the same one as Settings → Lizma → Language, so the two always agree and the phone's
dialogs and notification texts follow from the next launch. The app itself switches at once (`LanguageStore` rebuilds
the screens): texts put together in code use `String(localized: …, bundle: .app)`, so keep the `bundle: .app` in new
ones, and requests send the chosen language as `Accept-Language`. The app sends its language as `Accept-Language`, and the server answers
in it: its messages are translated by `src/main/resources/i18n/uk.json` (English text → Ukrainian), the legal pages have
`*_uk.html` versions.

Crash reports: none of our own code or third-party SDKs. Reports from TestFlight and App Store users who share analytics
arrive in Xcode → Window → Organizer → Crashes (the release build uploads its dSYM symbols).

## Music

The composer searches Apple's catalog with the iTunes Search API straight from the phone (no key, no account, the
search text never reaches our server) and posts the song with its cover, 30-second preview and Apple Music link
(`TrustedMediaUrls` accepts only Apple's and Spotify's own hosts). The player sheet plays the preview and opens the song
in Apple Music, or in Spotify: with the exact track when the server knows it, otherwise Spotify's search for it.

Exact Spotify links come from song.link (`MusicLinkResolver`, after a post is saved and every 2 minutes for posts it
couldn't do yet). song.link retired its keyless API in 2026 and gives keys only on request (odesli.co/help), so lookups
run only with `ODESLI_API_KEY`; without it nothing is sent anywhere. Older posts made with Spotify keep their Spotify link
and play in Spotify's embedded player.

## Push notifications

The server sends them for a friend request, a new friend, the first reaction of each friend to a post (a changed or
repeated reaction isn't news), comments on your post and replies to your comment, and at 13:00 Kyiv time a
"What are you listening to today?" to everyone who hasn't posted that day (`PushNotifier`,
`app.push.daily-reminder-cron` and `-zone`; with several server instances it would run on each). Nothing is sent about
yourself or between users who blocked each other, and each user chooses in Profile → Notifications
(`GET`/`PUT /user/me/notifications`). The text is a key of the app's strings (`loc-key`), so each phone shows it in its
own language; a tap opens the post, its comments, the profile, the Friends tab or the composer.

The app asks for permission after signing in and registers the phone's APNs token (`PUT /user/me/devices`, with
`SANDBOX` for debug builds); signing out forgets it (`POST /auth/logout` with `deviceToken`), and so do account deletion,
suspension and APNs reporting the app as removed.

Sending needs the paid Apple Developer Program: an APNs key (`APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_KEY_PATH` to the
`.p8` file) and the Push Notifications capability on the app (it adds the `aps-environment` entitlement). Without the key
the server writes each notification to its log instead. To see how notifications look and where a tap leads in the
simulator, send one by hand:

```
xcrun simctl push booted maxos-industry.lizma payload.apns
```

with a payload like `{"aps": {"alert": {"loc-key": "PUSH_COMMENT", "loc-args": ["Liza", "Love it"]}}, "type": "comment", "postId": 1}`.

## Screenshots and emoji

Reports and feedback can carry up to 3 screenshots. The app uploads each one first (`POST /user/attachments`, JPEG or
PNG up to 10 MB) and sends their ids as `attachmentIds`. The server keeps them as JPEGs of at most 2000 px, without
metadata (`UploadedImages`), linked to that report or feedback. Only moderators can open them
(`GET /admin/attachments/{id}.jpg`, never cached by shared caches). The moderators' email says how many there are but
doesn't carry them. Uploads that were never sent are deleted after a day (`app.attachments.cleanup-cron`). They also go
with the account of whoever sent them, and with the reports about an account that's deleted.

Reactions take any single emoji, however it's built: skin tones, joined sequences, flags, keycaps, and ones newer than
the server's Java (`PostService.isSingleEmoji`). Text or two emoji aren't a reaction. Comments keep every emoji as
written (the database is utf8mb4). The emoji picker has a few rows of favourites, plus a field that opens the phone's
own emoji keyboard, with its search, skin tones and recently used.

## Home screen widget

The `LizmaWidget` extension shows friends' latest songs: on the home screen the newest one (small), two (medium) or four
(large), each with its cover, who shared it and how long ago; on the lock screen who and what. A tap opens that post in
the app (`lizma://post/<id>`).

The widget never gets the user's tokens. After signing in, the app asks the server for a widget key
(`POST /user/me/widget-key` with the session's refresh token) and puts it, with the server address and the app's
language, in the App Group `group.maxos-industry.lizma` that the app and the widget share (`lizma/Shared`). The widget
calls `GET /widget/feed` with the `X-Widget-Key` header, which returns friends' latest 4 posts and nothing else, and keeps
the last songs and pictures in the group's folder for when the phone is offline. The server stores only the key's SHA-256.
The key belongs to that sign-in: signing out, the session ending, suspension and account deletion make it useless (401,
and the widget asks to open Lizma), and keys of ended sessions are deleted every night (`app.widgets.cleanup-cron`).

iOS decides when a widget refreshes; this one asks for about every 15 minutes. The app also refreshes it whenever it's
opened, and when someone posts, the server sends a silent push to those friends whose widget fetched in the last 3 days
(at most one per phone in 15 minutes), so the widget catches up without the app being opened. That push needs APNs like
the notifications: in the simulator `xcrun simctl push` doesn't reach an app that has no push token, so there opening the
app is what refreshes it. To try the widget in the simulator, add it in the Today View (swipe right on the first home
screen page, long-press, Edit → Add Widget): dropping a widget onto the home screen grid can crash SpringBoard in the
iOS 26.3 simulator (an Apple bug in its ripple animation). Dragging a widget's corner in edit mode changes its size.

## Moderation

Users report posts, comments and users, and send feedback (ideas, problems; optionally with an email for an answer).
Moderators are exactly the usernames in `ADMIN_USERNAMES` in `.env` (comma-separated): at startup they get `ROLE_ADMIN`
and every other account loses it. Sign the account up first, then add it and restart. A moderator account can't be deleted
or suspended from the app, so a listed username never becomes free for someone else.

Moderators handle everything in the app: Profile → **Moderation** (with a badge of what's waiting) has the open and handled
reports, the feedback inbox, user search with suspend/reinstate/delete, and a log of who did what.
New reports and feedback are also emailed to the support mailbox (see Email). The same works through the API:

| Request | What it does |
|---|---|
| `GET /admin/summary` | Open reports and new feedback (the badge) |
| `GET /admin/reports` (`?status=OPEN`, or `RESOLVED` and/or `DISMISSED`) | Open reports oldest first; handled ones newest first, with the decision and who made it |
| `POST /admin/reports/{id}/resolve` `{"action": "REMOVE_CONTENT"}` | Deletes the reported post or comment |
| `POST /admin/reports/{id}/resolve` `{"action": "BAN_USER"}` | Suspends the author, see below |
| `POST /admin/reports/{id}/resolve` `{"action": "DISMISS"}` | Closes the report, nothing is removed |
| `POST /admin/users/{username}/ban` | Suspends a user without a report |
| `POST /admin/users/{username}/unban` | Lifts a suspension (removed content does not come back) |
| `DELETE /admin/users/{username}` | Deletes an account with all of its data (e.g. a deletion request sent to support) |
| `GET /admin/users?query=` | Users, suspended ones included; without a query, the suspended users |
| `GET /admin/feedback?status=NEW\|DONE`, `POST /admin/feedback/{id}/done\|reopen` | The feedback inbox |
| `GET /admin/log` | Moderators' actions, newest first |

A suspended user can't sign in and their tokens stop working. Everything they posted (posts, comments, likes, reactions)
and their connections (friends, requests, invite links, music accounts) are deleted; the account row stays, so the username
can't be registered again. Nobody can find or add them. Moderators can't be suspended or deleted this way.

### Email

Every 5 minutes the server mails everything new (reports and feedback) as one digest to `NOTIFY_TO` (by default the support
address, `app.legal.contact-email`); feedback that left an email address gets its own mail with Reply-To set, so answering
is one click. An item is marked as mailed only after the mail went out, so nothing is lost while the mail server is down.
Without `MAIL_HOST` nothing is sent. For Gmail, turn on 2-Step Verification for the support account, create an app password
(Google account → Security → App passwords) and put into `.env`:

```
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=lizma.support@gmail.com
MAIL_PASSWORD=<the 16-character app password>
```

Gmail allows about 500 mails a day, plenty for this. Moving to a mail service (Brevo, Resend, Amazon SES) once the app has
its own domain only changes these settings.

## Before the App Store (needs the paid Apple Developer Program)

Done and tested without the paid account: push notifications end to end on the server (with a stand-in for APNs) and in
the simulator (`xcrun simctl push`), and the home screen widget in the simulator. Still to do and check once the account
exists:

- **Signing**: the team in Xcode, the App ID `maxos-industry.lizma` registered.
- **Push capability**: Signing & Capabilities → + Push Notifications (adds `aps-environment`).
- **APNs key**: Certificates, Identifiers & Profiles → Keys → + → Apple Push Notifications service. Download the `.p8`
  (only possible once) and note its Key ID; the Team ID is under Membership. Put `APNS_TEAM_ID`, `APNS_KEY_ID`,
  `APNS_KEY_PATH` in `.env` and restart: the log says "Push notifications go through APNs".
- **Real delivery, debug build on an iPhone** (sandbox): the permission prompt; a `device_tokens` row with `SANDBOX`.
  From a second account send a friend request, react and comment, and check that each notification arrives and its
  tap opens the right screen. Then check that Profile → Notifications switches each kind off, that signing out removes
  the token, and that the texts come in Ukrainian on a Ukrainian phone. For the 13:00 Kyiv reminder, set
  `app.push.daily-reminder-cron` to a time a few minutes ahead.
- **Widget on an iPhone**: automatic signing registers the App Group for both App IDs (`maxos-industry.lizma` and
  `maxos-industry.lizma.LizmaWidget`; the free account can do this too, only the silent push needs the paid one). Add the
  widget while signed in and check that friends' songs show, that a friend's new post reaches it within a minute or so
  with Lizma in the background (not swiped away), and that signing out turns it into "Open Lizma to sign in".
- **TestFlight build** (production APNs): the same checks, the token arrives as `PRODUCTION`.
- **App icon**: the app has none yet. Add an asset catalog with a 1024×1024 AppIcon (required for upload).
- **Server online before TestFlight testers and App Review**: a public HTTPS domain, set in
  `AppConfig.apiBaseURL` and `app.externalBaseUrl`. Invite links as Universal Links need Associated Domains (paid
  account too) and an `apple-app-site-association` file. Production `SECRET_KEY` and `ENCRYPTION_KEY`; register the
  admins before listing them in `ADMIN_USERNAMES`; the real operator name in `app.legal.operator-name`.
- **App Store Connect**: the app record, screenshots, description, the support and privacy policy URLs.
  The App Privacy answers must match `PrivacyInfo.xcprivacy`: user ID, name, photos, user content, customer support,
  email, no tracking. Also the age rating (user-generated content), and review notes on reporting, blocking and
  moderation with a demo account that has a friend and posts.
- **Export compliance**: the app uses only HTTPS, so `ITSAppUsesNonExemptEncryption = NO` in Info.plist saves the
  question on every upload.
- **Optional**: full in-app Apple Music playback (MusicKit service on the App ID); a song.link key for exact Spotify links;
  once the app needs iOS 26 anyway, WidgetKit push updates (`apns-push-type: widgets`) instead of the silent push, which
  also reach the widget after the app was swiped away.

## Configuration

| Property | Source | Meaning |
|---|---|---|
| `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `.env` | MySQL connection (also used by `docker-compose.yml`) |
| `SECRET_KEY` | `.env` | Base64 HMAC key for JWTs, at least 32 bytes |
| `ENCRYPTION_KEY` | `.env` | Base64 of 32 bytes; encrypts music service tokens in the database (AES-GCM). Optional in development (derived from `SECRET_KEY`), set it in production and keep it |
| `ADMIN_USERNAMES` | `.env` | Moderators, comma-separated |
| `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `NOTIFY_TO` | `.env` | Moderation emails (see Email) |
| `APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_KEY_PATH` (or `APNS_KEY`), `APNS_TOPIC` | `.env` | Push notifications through APNs (see Push notifications); without them they are only logged |
| `ODESLI_API_KEY` | `.env` | song.link key for exact Spotify links (see Music); optional |
| `app.push.daily-reminder-cron`, `app.push.daily-reminder-zone` | `application.properties` | When the daily reminder goes out (`0 0 13 * * *`, `Europe/Kyiv`) |
| `app.widgets.cleanup-cron` | `application.properties` | When widget keys of ended sign-ins are deleted (`0 45 4 * * *`) |
| `token.ttl`, `token.refresh-ttl` | `application.properties` | Access token (`15m`) and session (`30d`) lifetimes |
| `app.auth.*` | `application.properties` | Sign-in and sign-up limits |
| `app.externalBaseUrl` | `application.properties` | Base URL used in invite links |
| `spotify.clientId`, `spotify.redirectUri` | `application.properties` | Spotify PKCE sign-in on the server (the app no longer offers it, see Music) |
| `app.legal.operator-name`, `app.legal.contact-email` | `application.properties` | Shown on `/terms`, `/privacy` and as "Contact support" in the app |

Real environment variables override `.env`.
