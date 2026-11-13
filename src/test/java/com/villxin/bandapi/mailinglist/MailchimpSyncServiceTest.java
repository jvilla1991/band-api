package com.villxin.bandapi.mailinglist;

import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.entity.Subscriber.MailchimpStatus;
import com.villxin.bandapi.entity.Subscriber.Source;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.repository.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** How each Mailchimp answer moves a subscriber between states. */
class MailchimpSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private final MailchimpClient client = mock(MailchimpClient.class);
    private final SubscriberRepository repository = mock(SubscriberRepository.class);
    private final MailchimpSyncService service =
            new MailchimpSyncService(client, repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void configured() {
        when(client.isConfigured()).thenReturn(true);
    }

    private static Subscriber subscriber(String email, Source source) {
        Subscriber s = new Subscriber();
        s.setEmail(email);
        s.setSource(source);
        s.setMailchimpStatus(source == Source.LEGACY ? MailchimpStatus.LEGACY : MailchimpStatus.PENDING);
        return s;
    }

    private static HttpClientErrorException clientError(HttpStatus status, String title) {
        byte[] body = ("{\"title\":\"" + title + "\",\"detail\":\"from Mailchimp\"}").getBytes(StandardCharsets.UTF_8);
        return HttpClientErrorException.create(status, status.getReasonPhrase(), null, body, StandardCharsets.UTF_8);
    }

    @Test
    void successMarksSyncedAndClearsOldError() {
        Subscriber fan = subscriber("fan@example.com", Source.SITE);
        fan.setMailchimpError("earlier timeout");

        assertEquals(MailchimpSyncService.Outcome.SYNCED, service.syncOne(fan));

        assertEquals(MailchimpStatus.SYNCED, fan.getMailchimpStatus());
        assertEquals(NOW, fan.getMailchimpSyncedAt());
        assertNull(fan.getMailchimpError());
        verify(client, never()).addTags(anyString(), anyList()); // site signups aren't tagged
        verify(repository).save(fan);
    }

    @Test
    void legacySubscribersGetTheLegacyTag() {
        Subscriber old = subscriber("old@example.com", Source.LEGACY);

        service.syncOne(old);

        verify(client).addTags("old@example.com", List.of(MailchimpSyncService.LEGACY_TAG));
        assertEquals(MailchimpStatus.SYNCED, old.getMailchimpStatus());
    }

    @Test
    void addressRefusalIsParkedAsRejected() {
        Subscriber fake = subscriber("asdf@asdf.com", Source.SITE);
        doThrow(clientError(HttpStatus.BAD_REQUEST, "Invalid Resource")).when(client).upsertSubscribed(anyString());

        assertEquals(MailchimpSyncService.Outcome.REJECTED, service.syncOne(fake));

        assertEquals(MailchimpStatus.REJECTED, fake.getMailchimpStatus());
        assertEquals("Invalid Resource: from Mailchimp", fake.getMailchimpError());
    }

    @Test
    void temporaryFailuresStayPendingWithTheReason() {
        Subscriber a = subscriber("a@example.com", Source.SITE);
        doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null, null, null))
                .when(client).upsertSubscribed("a@example.com");
        Subscriber b = subscriber("b@example.com", Source.SITE);
        doThrow(new ResourceAccessException("Read timed out")).when(client).upsertSubscribed("b@example.com");

        assertEquals(MailchimpSyncService.Outcome.RETRY_LATER, service.syncOne(a));
        assertEquals(MailchimpSyncService.Outcome.RETRY_LATER, service.syncOne(b));

        assertEquals(MailchimpStatus.PENDING, a.getMailchimpStatus());
        assertEquals(MailchimpStatus.PENDING, b.getMailchimpStatus());
        assertTrue(b.getMailchimpError().contains("Read timed out"));
    }

    @Test
    void badKeyStopsTheAdminSyncWithAnActionableError() {
        when(repository.findByMailchimpStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(subscriber("a@example.com", Source.SITE), subscriber("b@example.com", Source.SITE)));
        doThrow(clientError(HttpStatus.UNAUTHORIZED, "API Key Invalid")).when(client).upsertSubscribed(anyString());

        ApiException e = assertThrows(ApiException.class, () -> service.syncPending(false));

        assertEquals("MAILCHIMP_CONFIG", e.getCode());
        verify(client).upsertSubscribed("a@example.com");
        verify(client, never()).upsertSubscribed("b@example.com"); // stopped at the first one
    }

    @Test
    void newSignupNeverThrowsEvenOnABadKey() {
        doThrow(clientError(HttpStatus.UNAUTHORIZED, "API Key Invalid")).when(client).upsertSubscribed(anyString());
        Subscriber fan = subscriber("fan@example.com", Source.SITE);

        assertDoesNotThrow(() -> service.trySyncNewSignup(fan));
        assertEquals(MailchimpStatus.PENDING, fan.getMailchimpStatus());
    }

    @Test
    void newSignupSkipsMailchimpWhenNotConfigured() {
        when(client.isConfigured()).thenReturn(false);

        service.trySyncNewSignup(subscriber("fan@example.com", Source.SITE));

        verify(client, never()).upsertSubscribed(anyString());
        verifyNoInteractions(repository);
    }

    @Test
    void legacyRowsAreOnlyIncludedWhenAsked() {
        service.syncPending(false);
        verify(repository).findByMailchimpStatusInOrderByIdAsc(List.of(MailchimpStatus.PENDING));

        service.syncPending(true);
        verify(repository).findByMailchimpStatusInOrderByIdAsc(List.of(MailchimpStatus.PENDING, MailchimpStatus.LEGACY));
    }

    @Test
    void adminSyncRefusesWhenNotConfigured() {
        when(client.isConfigured()).thenReturn(false);

        ApiException e = assertThrows(ApiException.class, () -> service.syncPending(false));

        assertEquals("MAILCHIMP_DISABLED", e.getCode());
    }
}
