-- Music service tokens are stored encrypted now (TokenCipher), which makes them longer.
ALTER TABLE `user_music_accounts`
  MODIFY `spotify_access_token` varchar(1024) DEFAULT NULL,
  MODIFY `spotify_refresh_token` varchar(1024) DEFAULT NULL,
  MODIFY `apple_user_token` varchar(1024) DEFAULT NULL;
