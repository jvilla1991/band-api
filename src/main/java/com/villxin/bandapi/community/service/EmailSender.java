package com.villxin.bandapi.community.service;

/**
 * Outbound email seam for the community feature (magic links).
 *
 * <p>Production (SES) seam: add an SES-backed implementation annotated
 * {@code @Profile("!local")} using the AWS SDK v2 {@code sesv2} client, and
 * restrict {@link LoggingEmailSender} to {@code @Profile("local")}. No AWS
 * dependency is included yet by design — dozens of users, local-first.</p>
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
