package com.example.lizma.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs ({@link com.example.lizma.service.ModerationMailer}) and work done after a request
 *  has answered ({@link com.example.lizma.service.MusicLinkResolver}). */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
