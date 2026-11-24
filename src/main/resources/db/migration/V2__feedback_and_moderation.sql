-- Feedback from the app, who handled a report and how, a log of moderators' actions,
-- and the "already emailed" marks the notification digest relies on.

CREATE TABLE `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` bigint NOT NULL,
  `category` enum('IDEA','PROBLEM','OTHER') NOT NULL,
  `message` varchar(2000) NOT NULL,
  `contact_email` varchar(254) DEFAULT NULL,
  `app_version` varchar(32) DEFAULT NULL,
  `os_version` varchar(32) DEFAULT NULL,
  `status` enum('NEW','DONE') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `handled_by_id` bigint DEFAULT NULL,
  `notified_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_status_created` (`status`, `created_at`),
  KEY `idx_feedback_author_created` (`author_id`, `created_at`),
  KEY `idx_feedback_notified` (`notified_at`),
  CONSTRAINT `fk_feedback_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_feedback_handled_by` FOREIGN KEY (`handled_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `moderation_actions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `moderator_id` bigint DEFAULT NULL,
  `action` enum('REMOVE_CONTENT','BAN_USER','UNBAN_USER','DELETE_USER','DISMISS_REPORT') NOT NULL,
  `target_username` varchar(50) DEFAULT NULL,
  `report_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_moderation_actions_created` (`created_at`),
  CONSTRAINT `fk_moderation_actions_moderator` FOREIGN KEY (`moderator_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `reports`
  ADD COLUMN `resolution` enum('REMOVE_CONTENT','BAN_USER','DISMISS') DEFAULT NULL,
  ADD COLUMN `resolved_by_id` bigint DEFAULT NULL,
  ADD COLUMN `notified_at` datetime(6) DEFAULT NULL,
  ADD KEY `idx_reports_status_created` (`status`, `created_at`),
  ADD KEY `idx_reports_reporter_created` (`reporter_id`, `created_at`),
  ADD KEY `idx_reports_notified` (`notified_at`),
  ADD CONSTRAINT `fk_reports_resolved_by` FOREIGN KEY (`resolved_by_id`) REFERENCES `users` (`id`);

-- Reports filed before notifications existed are not emailed now.
UPDATE `reports` SET `notified_at` = COALESCE(`resolved_at`, `created_at`);
