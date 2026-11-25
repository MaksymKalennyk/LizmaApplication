-- The share extension (posting a song shared from Apple Music or Spotify) gets a key of its own, like the home
-- screen widget has: both belong to one sign-in and stop working when it ends. They share one table, with one key
-- per sign-in and purpose; the widget's keys become WIDGET ones.
RENAME TABLE `widget_keys` TO `extension_keys`;

ALTER TABLE `extension_keys`
  ADD COLUMN `purpose` enum('WIDGET','SHARE') NOT NULL DEFAULT 'WIDGET' AFTER `family_id`,
  ADD UNIQUE KEY `uk_extension_keys_family_purpose` (`family_id`, `purpose`),
  DROP INDEX `uk_widget_keys_family`;
