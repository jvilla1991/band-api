package com.villxin.bandapi.community;

import com.jayway.jsonpath.JsonPath;
import com.villxin.bandapi.community.repository.MagicLinkTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Magic-link signup -> verify -> username claim -> JWT session, plus failure modes. */
class CommunityAuthFlowTest extends AbstractCommunityIntegrationTest {

    @Autowired
    private CapturingEmailSenderConfig.CapturingEmailSender emails;

    @Autowired
    private MagicLinkTokenRepository tokenRepository;

    private void requestSignup(String email, String username) throws Exception {
        mockMvc.perform(post("/api/community/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void signupVerifyClaimAndUseSession() throws Exception {
        requestSignup("fan1@example.com", "fan_one");
        String token = emails.lastTo("fan1@example.com").token();

        String verifyBody = mockMvc.perform(post("/api/community/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fan_one"))
                .andExpect(jsonPath("$.official").value(false))
                .andReturn().getResponse().getContentAsString();

        String jwt = JsonPath.read(verifyBody, "$.token");
        mockMvc.perform(get("/api/community/auth/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("fan1@example.com"))
                .andExpect(jsonPath("$.profile.username").value("fan_one"));

        // public profile is readable without auth
        mockMvc.perform(get("/api/community/profiles/fan_one"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fan_one"));
    }

    @Test
    void takenUsernameIsRejectedAtSignup() throws Exception {
        createMember("fan_taken", false);
        mockMvc.perform(post("/api/community/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"username\":\"fan_taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void invalidUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/api/community/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"username\":\"No Spaces!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_INVALID"));
    }

    @Test
    void expiredLinkIsRejectedWithDistinctCode() throws Exception {
        requestSignup("fan2@example.com", "fan_two");
        String token = emails.lastTo("fan2@example.com").token();

        // force-expire the token in the DB
        tokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals("fan2@example.com"))
                .forEach(t -> {
                    t.setExpiresAt(Instant.now().minusSeconds(60));
                    tokenRepository.save(t);
                });

        mockMvc.perform(post("/api/community/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
    }

    @Test
    void linkIsSingleUse() throws Exception {
        requestSignup("fan3@example.com", "fan_three");
        String token = emails.lastTo("fan3@example.com").token();

        mockMvc.perform(post("/api/community/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/community/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
    }

    @Test
    void unknownTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/community/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));
    }

    @Test
    void loginFlowIssuesSessionForExistingMember() throws Exception {
        createMember("fan_login", false);

        mockMvc.perform(post("/api/community/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"fan_login@test.example\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        String token = emails.lastTo("fan_login@test.example").token();
        mockMvc.perform(post("/api/community/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fan_login"));
    }

    @Test
    void loginForUnknownEmailReturnsNoAccount() throws Exception {
        mockMvc.perform(post("/api/community/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ACCOUNT"));
    }
}
