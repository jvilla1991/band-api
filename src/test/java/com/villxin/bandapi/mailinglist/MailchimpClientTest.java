package com.villxin.bandapi.mailinglist;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Wire format of the two Mailchimp calls, checked against a mock HTTP server. */
class MailchimpClientTest {

    private static final String KEY = "abc123def456-us5";
    // md5("fan@example.com") and base64("villxin-band-api:" + KEY), computed outside the code under test
    private static final String FAN_HASH = "b0215f672117e282a7b3bbcbb8157ba1";
    private static final String BASIC_AUTH = "Basic dmlsbHhpbi1iYW5kLWFwaTphYmMxMjNkZWY0NTYtdXM1";
    private static final String MEMBER_URL = "https://us5.api.mailchimp.com/3.0/lists/aud123/members/" + FAN_HASH;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MailchimpClient client = new MailchimpClient(builder, KEY, "aud123");

    @Test
    void upsertSendsStatusIfNewAndNeverStatus() {
        server.expect(requestTo(MEMBER_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", BASIC_AUTH))
                .andExpect(jsonPath("$.email_address").value("fan@example.com"))
                .andExpect(jsonPath("$.status_if_new").value("subscribed"))
                // sending "status" would re-subscribe people who unsubscribed in Mailchimp
                .andExpect(jsonPath("$.status").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.upsertSubscribed("fan@example.com");

        server.verify();
    }

    @Test
    void addTagsPostsActiveTags() {
        server.expect(requestTo(MEMBER_URL + "/tags"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.tags[0].name").value("legacy-first-release"))
                .andExpect(jsonPath("$.tags[0].status").value("active"))
                .andRespond(withSuccess());

        client.addTags("fan@example.com", List.of("legacy-first-release"));

        server.verify();
    }

    @Test
    void subscriberHashIgnoresCaseAndSurroundingSpace() {
        assertEquals(FAN_HASH, MailchimpClient.subscriberHash("fan@example.com"));
        assertEquals(FAN_HASH, MailchimpClient.subscriberHash("  Fan@Example.COM "));
    }

    @Test
    void errorsAreDescribedFromMailchimpsBody() {
        server.expect(requestTo(MEMBER_URL))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"title\":\"Invalid Resource\",\"detail\":\"looks fake or invalid\"}"));

        RestClientResponseException e = assertThrows(RestClientResponseException.class,
                () -> client.upsertSubscribed("fan@example.com"));

        assertEquals("Invalid Resource: looks fake or invalid", MailchimpClient.describe(e));
    }

    @Test
    void offUnlessKeyAndAudienceAreBothSet() {
        assertTrue(client.isConfigured());
        assertFalse(new MailchimpClient(RestClient.builder(), "", "aud123").isConfigured());
        assertFalse(new MailchimpClient(RestClient.builder(), KEY, " ").isConfigured());
        assertFalse(new MailchimpClient(RestClient.builder(), null, null).isConfigured());
        // a key without its "-us5" suffix can't be routed to a data center
        assertFalse(new MailchimpClient(RestClient.builder(), "abc123def456", "aud123").isConfigured());
    }

    @Test
    void serverPrefixRejectsAnythingThatIsNotADataCenter() {
        assertEquals("us5", MailchimpClient.serverPrefix("abc-us5"));
        assertEquals("us21", MailchimpClient.serverPrefix("abc-us21"));
        assertNull(MailchimpClient.serverPrefix("abc-"));
        assertNull(MailchimpClient.serverPrefix("abc-evil.example.com/x"));
        assertNull(MailchimpClient.serverPrefix("abc"));
    }
}
