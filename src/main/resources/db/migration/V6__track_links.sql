-- Phase 4: a post can be listened to anywhere. Tracks come from the Apple Music catalog (iTunes Search):
-- a 30-second preview and the Apple Music link; the same track's Spotify link is looked up afterwards
-- (song.link). links_resolved_at marks posts whose links were looked up, successfully or not.

ALTER TABLE `posts`
  ADD COLUMN `preview_url` varchar(512) DEFAULT NULL,
  ADD COLUMN `apple_music_url` varchar(512) DEFAULT NULL,
  ADD COLUMN `spotify_url` varchar(512) DEFAULT NULL,
  ADD COLUMN `links_resolved_at` datetime(6) DEFAULT NULL,
  ADD KEY `idx_posts_links_resolved` (`links_resolved_at`);
