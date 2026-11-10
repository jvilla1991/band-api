package com.villxin.bandapi.mailinglist;

import com.villxin.bandapi.community.AbstractCommunityIntegrationTest;
import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.entity.Subscriber.MailchimpStatus;
import com.villxin.bandapi.entity.Subscriber.Source;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.repository.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack: POST /api/subscribe through to the database and the (mocked)
 * Mailchimp client, plus the admin status/sync endpoints and their security.
 */
class MailingListFlowTest extends AbstractCommunityIntegrationTest {

    @MockBean
    private MailchimpClient mailchimp;

    @Autowired
    private SubscriberRepository subscribers;

    @Autowired
    private SignupRateLimiter rateLimiter;

    @BeforeEach
    void clean() {
        subscribers.deleteAll();
        rateLimiter.reset();
        when(mailchimp.isConfigured()).thenReturn(true);
    }

    private ResultActions signup(String json) throws Exception {
        return mockMvc.perform(post("/api/subscribe").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Subscriber row(String email) {
        return subscribers.findAll().stream().filter(s -> s.getEmail().equals(email)).findFirst().orElseThrow();
    }

    private String adminBearer() {
        User admin = userRepository.findByEmail("list-admin@test.example").orElseGet(() -> {
            User user = new User();
            user.setEmail("list-admin@test.example");
            user.setRole("ADMIN");
            return userRepository.save(user);
        });
        return bearer(admin);
    }

    @Test
    void signupIsSavedThenSentToMailchimp() throws Exception {
        signup("{\"email\":\"  Fan1@Example.com \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(mailchimp).upsertSubscribed("fan1@example.com");
        Subscriber saved = row("fan1@example.com");
        assertEquals(Source.SITE, saved.getSource());
        assertEquals(MailchimpStatus.SYNCED, saved.getMailchimpStatus());
        assertNotNull(saved.getMailchimpSyncedAt());
    }

    @Test
    void signupStillSucceedsWhenMailchimpIsDown() throws Exception {
        doThrow(new ResourceAccessException("connect timed out")).when(mailchimp).upsertSubscribed(anyString());

        signup("{\"email\":\"fan2@example.com\"}").andExpect(status().isOk());

        Subscriber saved = row("fan2@example.com");
        assertEquals(MailchimpStatus.PENDING, saved.getMailchimpStatus());
        assertNotNull(saved.getMailchimpError());
    }

    @Test
    void signupStillSucceedsWithABadMailchimpKey() throws Exception {
        doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null))
                .when(mailchimp).upsertSubscribed(anyString());

        signup("{\"email\":\"fan3@example.com\"}").andExpect(status().isOk());

        assertEquals(MailchimpStatus.PENDING, row("fan3@example.com").getMailchimpStatus());
    }

    @Test
    void signupWorksWithMailchimpOff() throws Exception {
        when(mailchimp.isConfigured()).thenReturn(false);

        signup("{\"email\":\"fan4@example.com\"}").andExpect(status().isOk());

        verify(mailchimp, never()).upsertSubscribed(anyString());
        assertEquals(MailchimpStatus.PENDING, row("fan4@example.com").getMailchimpStatus());
    }

    @Test
    void existingResponseContractIsUnchanged() throws Exception {
        signup("{\"email\":\"dup@example.com\"}").andExpect(status().isOk());
        signup("{\"email\":\"DUP@example.com\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate"));
        signup("{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid"));
    }

    @Test
    void honeypotLooksLikeSuccessButStoresNothing() throws Exception {
        signup("{\"email\":\"bot@example.com\",\"website\":\"http://spam.example\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        assertFalse(subscribers.existsByEmail("bot@example.com"));
        verify(mailchimp, never()).upsertSubscribed(anyString());
    }

    @Test
    void tooManySignupsFromOneClientAreRefused() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/subscribe")
                            .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"burst" + i + "@example.com\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/subscribe")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"burst6@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));

        assertFalse(subscribers.existsByEmail("burst6@example.com"));
    }

    @Test
    void adminSyncRetriesPendingAndImportsLegacyOnlyWhenAsked() throws Exception {
        Subscriber legacy = new Subscriber();
        legacy.setEmail("early-fan@example.com");
        legacy.setSource(Source.LEGACY);
        legacy.setMailchimpStatus(MailchimpStatus.LEGACY);
        subscribers.save(legacy);
        Subscriber stuck = new Subscriber();
        stuck.setEmail("stuck@example.com");
        subscribers.save(stuck); // PENDING by default

        String admin = adminBearer();

        mockMvc.perform(get("/api/mailing-list/admin/status").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.counts.LEGACY").value(1))
                .andExpect(jsonPath("$.counts.PENDING").value(1))
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(post("/api/mailing-list/admin/sync").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(1))
                .andExpect(jsonPath("$.counts.LEGACY").value(1));
        verify(mailchimp, never()).upsertSubscribed("early-fan@example.com");

        mockMvc.perform(post("/api/mailing-list/admin/sync?includeLegacy=true").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(1))
                .andExpect(jsonPath("$.counts.SYNCED").value(2));
        verify(mailchimp).addTags("early-fan@example.com", List.of(MailchimpSyncService.LEGACY_TAG));
    }

    @Test
    void adminSyncSaysWhenMailchimpIsOff() throws Exception {
        when(mailchimp.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/mailing-list/admin/sync").header("Authorization", adminBearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAILCHIMP_DISABLED"));
    }

    @Test
    void adminEndpointsRejectAnonymousAndNonAdminCallers() throws Exception {
        mockMvc.perform(get("/api/mailing-list/admin/status")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mailing-list/admin/sync")).andExpect(status().isForbidden());

        User fan = createMember("list_fan", false);
        mockMvc.perform(get("/api/mailing-list/admin/status").header("Authorization", bearer(fan)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mailing-list/admin/sync").header("Authorization", bearer(fan)))
                .andExpect(status().isForbidden());
    }
}
