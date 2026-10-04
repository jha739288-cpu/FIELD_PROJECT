package com.labmarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs (overdue detection, and later reminders). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
