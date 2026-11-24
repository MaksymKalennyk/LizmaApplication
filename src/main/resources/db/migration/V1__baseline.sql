-- Baseline: the schema as Hibernate generated it (ddl-auto=update) before migrations were introduced.
-- Databases created before that are marked as being at this version without running it
-- (spring.flyway.baseline-on-migrate), so the constraint names below must stay as they are.
-- From now on every schema change is a new V<n>__<description>.sql file; never edit an applied one.

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `password` varchar(255) NOT NULL,
  `role` enum('ROLE_ADMIN','ROLE_USER') NOT NULL,
  `username` varchar(50) NOT NULL,
  `banned` tinyint(1) NOT NULL DEFAULT '0',
  `terms_accepted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `album_cover_url` varchar(512) DEFAULT NULL,
  `artist_name` varchar(255) NOT NULL,
  `caption` varchar(1024) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `music_id` varchar(255) NOT NULL,
  `music_provider` enum('APPLE','SPOTIFY') NOT NULL,
  `track_name` varchar(255) NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6xvn0811tkyo3nfjk2xvqx6ns` (`author_id`),
  CONSTRAINT `FK6xvn0811tkyo3nfjk2xvqx6ns` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `post_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `author_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9uedrlupih4x9c9qk1ntwdpie` (`author_id`),
  KEY `FKc3b7s6wypcsvua2ycn4o1lv2c` (`parent_id`),
  KEY `FKaawaqxjs3br8dw5v90w7uu514` (`post_id`),
  CONSTRAINT `FK9uedrlupih4x9c9qk1ntwdpie` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKaawaqxjs3br8dw5v90w7uu514` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
  CONSTRAINT `FKc3b7s6wypcsvua2ycn4o1lv2c` FOREIGN KEY (`parent_id`) REFERENCES `post_comments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `comment_likes` (
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`comment_id`,`user_id`),
  KEY `FK6h3lbneryl5pyb9ykaju7werx` (`user_id`),
  CONSTRAINT `FK6h3lbneryl5pyb9ykaju7werx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6q90s6gsqjadphkngyf3y0fop` FOREIGN KEY (`comment_id`) REFERENCES `post_comments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `post_reactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `reaction_type` varchar(16) DEFAULT NULL,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqx1p0wtweq8mgrtenw93jdqns` (`post_id`,`user_id`),
  KEY `FKptar8f3u0qt7ssjksu2hxme03` (`user_id`),
  CONSTRAINT `FKptar8f3u0qt7ssjksu2hxme03` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKq9ivjiqt8flog43og7gtmoyqw` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `friend_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `status` enum('ACCEPTED','DECLINED','PENDING') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `recipient_id` bigint NOT NULL,
  `requester_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKps9kptamct3ij6nxh0efavr5p` (`requester_id`,`recipient_id`),
  KEY `FKmofsv4jjyk6flwplaqwigukl8` (`recipient_id`),
  CONSTRAINT `FKdw74r5l8n8ya5bn3107xqucp5` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmofsv4jjyk6flwplaqwigukl8` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `friendships` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `status` enum('BLOCKED','CONFIRMED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user1_id` bigint NOT NULL,
  `user2_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7kd70s6m5v1a744x2y9wqvvyr` (`user1_id`,`user2_id`),
  KEY `FK3ii24jylf37bx29q6navneqa7` (`user2_id`),
  CONSTRAINT `FK3ii24jylf37bx29q6navneqa7` FOREIGN KEY (`user2_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKbni8hh12wpbcinmrrm7icj9pa` FOREIGN KEY (`user1_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `friend_invites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `status` enum('ACCEPTED','EXPIRED','ISSUED') NOT NULL,
  `token` varchar(64) NOT NULL,
  `accepted_by_id` bigint DEFAULT NULL,
  `requester_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4tov2aapcqm9psytjqwwuugxk` (`token`),
  KEY `FK49hqqanjx1ugkokeaqjwvgq1v` (`accepted_by_id`),
  KEY `FKkiga7e7d3t4n5r0rcp8vgqvl0` (`requester_id`),
  CONSTRAINT `FK49hqqanjx1ugkokeaqjwvgq1v` FOREIGN KEY (`accepted_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKkiga7e7d3t4n5r0rcp8vgqvl0` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `details` varchar(500) DEFAULT NULL,
  `reason` enum('HARASSMENT','HATE','NUDITY','OTHER','SPAM','VIOLENCE') NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `status` enum('DISMISSED','OPEN','RESOLVED') NOT NULL,
  `target_id` bigint NOT NULL,
  `target_type` enum('COMMENT','POST','USER') NOT NULL,
  `reporter_id` bigint NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKd3qiw2om5d2oh5xb7fbdcq225` (`reporter_id`),
  KEY `FKpemp8lt5m0ujfywtgnuw4ypw9` (`target_user_id`),
  CONSTRAINT `FKd3qiw2om5d2oh5xb7fbdcq225` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpemp8lt5m0ujfywtgnuw4ypw9` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_blocks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `blocked_id` bigint NOT NULL,
  `blocker_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6kwyqs53ciqfxmlhquyq5socr` (`blocker_id`,`blocked_id`),
  KEY `FK7k3mfgb03bnmwh81vqb1u5h80` (`blocked_id`),
  CONSTRAINT `FK7k3mfgb03bnmwh81vqb1u5h80` FOREIGN KEY (`blocked_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKgvu85oyjrfafwttb7iphgmm0v` FOREIGN KEY (`blocker_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_music_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apple_storefront` varchar(8) DEFAULT NULL,
  `apple_user_token` varchar(512) DEFAULT NULL,
  `connected_at` datetime(6) DEFAULT NULL,
  `provider` enum('APPLE','SPOTIFY') NOT NULL,
  `spotify_access_token` varchar(512) DEFAULT NULL,
  `spotify_access_token_expires_at` datetime(6) DEFAULT NULL,
  `spotify_refresh_token` varchar(512) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKes7m5qvr6ybw326qm5s11olxb` (`user_id`,`provider`),
  CONSTRAINT `FK59ox3nv4ju8dy2jr2brenv131` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
