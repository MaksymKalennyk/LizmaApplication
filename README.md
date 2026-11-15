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

`./gradlew test` runs the API integration tests (`ApiIntegrationTest`) on an in-memory H2 database — no Docker needed.

## iOS app

Open `lizma/lizma.xcodeproj`, choose the `lizma` scheme and an iPhone simulator, run.
The app calls `http://localhost:8080` (`lizma/lizma/Config/AppConfig.swift`); a real device needs your Mac's IP there.

## Configuration

| Property | Source | Meaning |
|---|---|---|
| `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `.env` | MySQL connection (also used by `docker-compose.yml`) |
| `SECRET_KEY` | `.env` | Base64 HMAC key for JWTs, at least 32 bytes |
| `token.ttl` | `application.properties` | How long a sign-in stays valid (default `7d`) |
| `app.externalBaseUrl` | `application.properties` | Base URL used in invite links |
| `spotify.clientId`, `spotify.redirectUri` | `application.properties` | Spotify PKCE app settings |

Real environment variables override `.env`.
