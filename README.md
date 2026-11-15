# Lizma

Social music app: a Spring Boot backend (this folder) and a SwiftUI iOS client (`lizma/`).
Users add friends, share tracks to a friends-only feed, react with emoji and comment.

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
   (sign in via `POST /auth/sign-in`, press **Authorize** and paste the token).

Tables are created/updated by Hibernate (`ddl-auto=update`).

## Tests

`./gradlew test` runs the API integration tests (`ApiIntegrationTest`, `SafetyIntegrationTest`, `SpotifyIntegrationTest`)
on an in-memory H2 database — no Docker needed.

## iOS app

Open `lizma/lizma.xcodeproj`, choose the `lizma` scheme and an iPhone simulator, run.
The app calls `http://localhost:8080` (`lizma/lizma/Config/AppConfig.swift`). A debug build takes another address
from the `LIZMA_API_BASE_URL` environment variable (Xcode: Edit Scheme → Run → Arguments), e.g. your Mac's IP for a real device.

## Moderation

Users report posts, comments and users in the app; every report is stored and logged as a `WARN` line.
Moderators are accounts with `ROLE_ADMIN`: sign the account up first, then list its username in `app.admin-usernames`
(comma-separated) and restart the server. They work through the API (Swagger UI is the quickest way):

| Request | What it does |
|---|---|
| `GET /admin/reports` (`?status=OPEN\|RESOLVED\|DISMISSED`) | Reports, oldest first, with a preview of the content |
| `POST /admin/reports/{id}/resolve` `{"action": "REMOVE_CONTENT"}` | Deletes the reported post or comment |
| `POST /admin/reports/{id}/resolve` `{"action": "BAN_USER"}` | Suspends the author, see below |
| `POST /admin/reports/{id}/resolve` `{"action": "DISMISS"}` | Closes the report, nothing is removed |
| `POST /admin/users/{username}/ban` | Suspends a user without a report |
| `POST /admin/users/{username}/unban` | Lifts a suspension (removed content does not come back) |
| `DELETE /admin/users/{username}` | Deletes an account with all of its data (e.g. a deletion request sent to support) |

A suspended user can't sign in and their tokens stop working. Everything they posted (posts, comments, likes, reactions)
and their connections (friends, requests, invite links, music accounts) are deleted; the account row stays, so the username
can't be registered again. Nobody can find or add them. Moderators can't be suspended or deleted this way.

## Configuration

| Property | Source | Meaning |
|---|---|---|
| `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `.env` | MySQL connection (also used by `docker-compose.yml`) |
| `SECRET_KEY` | `.env` | Base64 HMAC key for JWTs, at least 32 bytes |
| `token.ttl` | `application.properties` | How long a sign-in stays valid (default `7d`) |
| `app.externalBaseUrl` | `application.properties` | Base URL used in invite links |
| `spotify.clientId`, `spotify.redirectUri` | `application.properties` | Spotify PKCE app settings |
| `app.admin-usernames` | `application.properties` | Accounts that get `ROLE_ADMIN` at startup (moderators) |
| `app.legal.operator-name`, `app.legal.contact-email` | `application.properties` | Shown on `/terms`, `/privacy` and as "Contact support" in the app |

Real environment variables override `.env`.
