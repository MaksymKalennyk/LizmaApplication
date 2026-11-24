-- Push notifications: the phones to send them to, and what each user wants to hear about.
-- A device token belongs to whoever signed in on that phone last. APNs keeps development builds apart
-- (its sandbox), so each token says which environment it came from.

CREATE TABLE `device_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token` varchar(200) NOT NULL,
  `environment` enum('SANDBOX','PRODUCTION') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_tokens_token` (`token`),
  KEY `idx_device_tokens_user` (`user_id`),
  CONSTRAINT `fk_device_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Profile -> Notifications. Everything is on until the user turns it off (the phone asks for permission first).
ALTER TABLE `users`
  ADD COLUMN `push_friend_requests` tinyint(1) NOT NULL DEFAULT '1',
  ADD COLUMN `push_reactions` tinyint(1) NOT NULL DEFAULT '1',
  ADD COLUMN `push_comments` tinyint(1) NOT NULL DEFAULT '1',
  ADD COLUMN `push_daily_reminder` tinyint(1) NOT NULL DEFAULT '1';
