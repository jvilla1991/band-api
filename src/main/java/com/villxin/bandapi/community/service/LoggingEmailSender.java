package com.villxin.bandapi.community.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev implementation: logs the email instead of sending it. This is the only
 * place a magic link is ever written to the log — the dev workaround for not
 * having a real mail provider. Replace with an SES implementation for
 * production (see {@link EmailSender}).
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[DEV EMAIL] to={} subject={}\n{}", to, subject, body);
    }
}
