-- Profiles: a display name and a photo. The photo lives in its own table under a random token, which is
-- also its public URL (/avatars/<token>.jpg): a new photo gets a new token, so caches never show the old one.

ALTER TABLE `users`
  ADD COLUMN `display_name` varchar(50) DEFAULT NULL,
  ADD COLUMN `avatar_token` varchar(40) DEFAULT NULL,
  ADD UNIQUE KEY `uk_users_avatar_token` (`avatar_token`);

CREATE TABLE `user_avatars` (
  `token` varchar(40) NOT NULL,
  `user_id` bigint NOT NULL,
  `image` mediumblob NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`token`),
  UNIQUE KEY `uk_user_avatars_user` (`user_id`),
  CONSTRAINT `fk_user_avatars_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `moderation_actions`
  MODIFY `action` enum('REMOVE_CONTENT','BAN_USER','UNBAN_USER','DELETE_USER','DISMISS_REPORT','REMOVE_PROFILE') NOT NULL;
