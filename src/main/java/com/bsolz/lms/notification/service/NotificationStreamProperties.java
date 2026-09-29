package com.bsolz.lms.notification.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-sent event streams ({@code lms.notification.stream}).
 *
 * @param timeout how long a stream stays open; clients reconnect after it closes
 * @param heartbeat how often an idle stream gets a comment line, so proxies and load balancers
 * (e.g. an ALB's 60 second idle timeout) keep it open
 * @param maxPerUser open streams per user (browser tabs); opening another closes the oldest
 */
@ConfigurationProperties("lms.notification.stream")
public record NotificationStreamProperties(Duration timeout, Duration heartbeat, int maxPerUser) {
}
