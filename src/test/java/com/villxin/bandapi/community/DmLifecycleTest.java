package com.villxin.bandapi.community;

import com.jayway.jsonpath.JsonPath;
import com.villxin.bandapi.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request -> accept/decline -> thread -> revoke/reopen lifecycle + folders. */
class DmLifecycleTest extends AbstractCommunityIntegrationTest {

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = userRepository.findByUsername("dm_alice").orElseGet(() -> createMember("dm_alice", false));
        bob = userRepository.findByUsername("dm_bob").orElseGet(() -> createMember("dm_bob", false));
    }

    private void sendRequest(User from, String to, String message) throws Exception {
        mockMvc.perform(post("/api/community/dms/requests")
                        .header("Authorization", bearer(from))
                        .contentType(APPLICATION_JSON)
                        .content("{\"toUsername\":\"" + to + "\",\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void fullLifecycle_requestAcceptMessageRevokeReopen() throws Exception {
        // word filter applies to the request body ("damn" is on the default blocklist)
        sendRequest(alice, "dm_bob", "hey damn nice profile");

        // bob sees the pending request with a filtered preview
        String requests = mockMvc.perform(get("/api/community/dms/requests")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].from.username").value("dm_alice"))
                .andExpect(jsonPath("$[0].preview").value("hey d*** nice profile"))
                .andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(requests, "$[0].id");

        // accept -> thread opens, request body becomes first message
        String acceptBody = mockMvc.perform(post("/api/community/dms/requests/" + requestId + "/accept")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int threadId = JsonPath.read(acceptBody, "$.threadId");

        // bob's inbox: 1 unread (alice's first message)
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(threadId))
                .andExpect(jsonPath("$[0].with.username").value("dm_alice"))
                .andExpect(jsonPath("$[0].unreadCount").value(1));

        // reading marks it read
        mockMvc.perform(get("/api/community/dms/threads/" + threadId).header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].from").value("dm_alice"))
                .andExpect(jsonPath("$.messages[0].body").value("hey d*** nice profile"));
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$[0].unreadCount").value(0));

        // two-way: bob replies, alice sees unread
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/messages")
                        .header("Authorization", bearer(bob))
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"hey alice\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$[0].unreadCount").value(1));

        // bob revokes access: closed both ends
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/revoke")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk());

        // alice can no longer post...
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/messages")
                        .header("Authorization", bearer(alice))
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"still there?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_CLOSED"));

        // ...and the thread is hidden from her inbox, while bob (the revoker) still sees it
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$[0].status").value("REVOKED"));

        // blocked: alice's new request LOOKS sent but bob never receives it
        sendRequest(alice, "dm_bob", "please talk to me");
        mockMvc.perform(get("/api/community/dms/requests").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.length()").value(0));

        // only the revoker can reopen; then messaging works again
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/reopen")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_REVOKER"));
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/reopen")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/messages")
                        .header("Authorization", bearer(alice))
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"we are back\"}"))
                .andExpect(status().isOk());

        // trash is per-user: alice trashes, bob unaffected
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/trash")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/community/dms/trash").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$[0].id").value(threadId));
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$[0].id").value(threadId));
        mockMvc.perform(post("/api/community/dms/threads/" + threadId + "/restore")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$[0].id").value(threadId));
    }

    @Test
    void declineIsSilentForTheSender() throws Exception {
        User carol = createMember("dm_carol", false);
        User dave = createMember("dm_dave", false);

        sendRequest(carol, "dm_dave", "hi dave");

        String requests = mockMvc.perform(get("/api/community/dms/requests")
                        .header("Authorization", bearer(dave)))
                .andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(requests, "$[0].id");

        mockMvc.perform(post("/api/community/dms/requests/" + requestId + "/decline")
                        .header("Authorization", bearer(dave)))
                .andExpect(status().isOk());

        // gone from dave's requests; no thread anywhere
        mockMvc.perform(get("/api/community/dms/requests").header("Authorization", bearer(dave)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/community/dms/inbox").header("Authorization", bearer(carol)))
                .andExpect(jsonPath("$.length()").value(0));

        // carol's Sent folder still shows the request as sent — no hint it was declined
        mockMvc.perform(get("/api/community/dms/sent").header("Authorization", bearer(carol)))
                .andExpect(jsonPath("$.requests[0].to.username").value("dm_dave"))
                .andExpect(jsonPath("$.requests[0].preview").value("hi dave"));

        // decline is not a block: carol may request again
        sendRequest(carol, "dm_dave", "hi again");
        mockMvc.perform(get("/api/community/dms/requests").header("Authorization", bearer(dave)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void dmEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/community/dms/inbox"))
                .andExpect(status().is4xxClientError());
    }
}
