-- Screenshots attached to a report or to feedback. The app uploads them first (only their owner can use them),
-- then sends their ids with the report or feedback. They're never public: only moderators can open them.
-- Uploads that never got sent are deleted after a day.

CREATE TABLE `attachments` (
  `token` varchar(40) NOT NULL,
  `owner_id` bigint NOT NULL,
  `image` mediumblob NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `report_id` bigint DEFAULT NULL,
  `feedback_id` bigint DEFAULT NULL,
  PRIMARY KEY (`token`),
  KEY `idx_attachments_owner` (`owner_id`),
  KEY `idx_attachments_report` (`report_id`),
  KEY `idx_attachments_feedback` (`feedback_id`),
  KEY `idx_attachments_created` (`created_at`),
  CONSTRAINT `fk_attachments_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_attachments_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`),
  CONSTRAINT `fk_attachments_feedback` FOREIGN KEY (`feedback_id`) REFERENCES `feedback` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
