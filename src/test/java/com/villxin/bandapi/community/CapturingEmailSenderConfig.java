package com.villxin.bandapi.community;

import com.villxin.bandapi.community.service.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Captures outbound email in memory so tests can extract magic links. */
@TestConfiguration
public class CapturingEmailSenderConfig {

    public record SentEmail(String to, String subject, String body) {
        /** The raw one-time token from the magic link inside the body. */
        public String token() {
            Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
            if (!m.find()) throw new IllegalStateException("No token in email body: " + body);
            return m.group(1);
        }
    }

    public static class CapturingEmailSender implements EmailSender {
        public final List<SentEmail> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new SentEmail(to, subject, body));
        }

        public SentEmail lastTo(String email) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i).to().equals(email)) return sent.get(i);
            }
            throw new IllegalStateException("No email captured for " + email);
        }
    }

    @Bean
    @Primary
    public CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }
}
