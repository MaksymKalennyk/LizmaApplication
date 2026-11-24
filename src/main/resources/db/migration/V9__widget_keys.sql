-- The home screen widget asks for friends' latest songs with a key of its own instead of the user's session: it
-- can't renew sessions itself (that would sign the app out). A key belongs to one sign-in (a refresh token family)
-- and stops working when that session ends: signing out on that phone, a suspension, or the session running out.
-- Only the key's SHA-256 is kept. last_used_at says the widget is on someone's screen.

CREATE TABLE `widget_keys` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `family_id` varchar(36) NOT NULL,
  `key_hash` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_widget_keys_hash` (`key_hash`),
  UNIQUE KEY `uk_widget_keys_family` (`family_id`),
  KEY `idx_widget_keys_user` (`user_id`),
  KEY `idx_widget_keys_used` (`last_used_at`),
  CONSTRAINT `fk_widget_keys_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
