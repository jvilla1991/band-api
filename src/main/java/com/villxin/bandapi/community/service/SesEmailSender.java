package com.villxin.bandapi.community.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * SES-backed sender, active only when {@code community.email.from} (env
 * EMAIL_FROM) is set — otherwise {@link LoggingEmailSender} handles email.
 * Region and credentials come from the App Runner instance role / default
 * provider chain. Note: while the SES account is in sandbox, delivery only
 * succeeds to verified recipients.
 */
@Component
@Primary
@ConditionalOnExpression("!'${community.email.from:}'.isEmpty()")
public class SesEmailSender implements EmailSender {

    private final SesV2Client client = SesV2Client.create();
    private final String from;

    public SesEmailSender(@Value("${community.email.from}") String from) {
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        client.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(d -> d.toAddresses(to))
                .content(c -> c.simple(m -> m
                        .subject(s -> s.data(subject).charset("UTF-8"))
                        .body(b -> b.text(t -> t.data(body).charset("UTF-8")))))
                .build());
    }
}
