package com.villxin.bandapi.community;

import com.jayway.jsonpath.JsonPath;
import com.villxin.bandapi.entity.User;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bulletins (official-only), forum, walls, profile edits. */
class CommunityContentTest extends AbstractCommunityIntegrationTest {

    @Test
    void onlyTheOfficialAccountCanPostBulletins() throws Exception {
        User official = createMember("villxin_official", true);
        User fan = createMember("bulletin_fan", false);

        // plain member: forbidden with a stable code
        mockMvc.perform(post("/api/community/bulletins")
                        .header("Authorization", bearer(fan))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"nope\",\"body\":\"nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OFFICIAL"));

        // anonymous: rejected by the security chain
        mockMvc.perform(post("/api/community/bulletins")
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"nope\",\"body\":\"nope\"}"))
                .andExpect(status().is4xxClientError());

        // official: allowed
        mockMvc.perform(post("/api/community/bulletins")
                        .header("Authorization", bearer(official))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"new demo up\",\"body\":\"listen now\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.official").value(true));

        // public list, newest first, no auth needed
        mockMvc.perform(get("/api/community/bulletins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("new demo up"));
    }

    @Test
    void forumThreadAndReplyAreFilteredAndPubliclyReadable() throws Exception {
        User fan = createMember("forum_fan", false);

        // boards are seeded by V4
        mockMvc.perform(get("/api/community/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("general"));

        String thread = mockMvc.perform(post("/api/community/boards/general/threads")
                        .header("Authorization", bearer(fan))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"damn good demo\",\"body\":\"that break was damn heavy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("d*** good demo"))
                .andReturn().getResponse().getContentAsString();
        int threadId = JsonPath.read(thread, "$.id");

        mockMvc.perform(post("/api/community/threads/" + threadId + "/replies")
                        .header("Authorization", bearer(fan))
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"hell yes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("h*** yes"));

        // anonymous read works; anonymous write does not
        mockMvc.perform(get("/api/community/threads/" + threadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("that break was d*** heavy"))
                .andExpect(jsonPath("$.replies.length()").value(1));
        mockMvc.perform(post("/api/community/threads/" + threadId + "/replies")
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"anon\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void profileUpdateWithThemeAndTopFriendsAndWallComments() throws Exception {
        User owner = createMember("profile_owner", false);
        User friend1 = createMember("profile_friend1", false);
        User friend2 = createMember("profile_friend2", false);

        mockMvc.perform(put("/api/community/profiles/me")
                        .header("Authorization", bearer(owner))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":"The Owner","mood":"atmospheric","profileSong":"demo-2",
                                 "about":"hi","whoToMeet":"other fans","themeAccent":"violet",
                                 "themeGlitter":true,"themeTiledBg":false,
                                 "topFriends":["profile_friend1","profile_friend2"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeAccent").value("VIOLET"))
                .andExpect(jsonPath("$.themeGlitter").value(true))
                .andExpect(jsonPath("$.topFriends[0].username").value("profile_friend1"))
                .andExpect(jsonPath("$.topFriends[1].username").value("profile_friend2"));

        // invalid accent -> stable code
        mockMvc.perform(put("/api/community/profiles/me")
                        .header("Authorization", bearer(owner))
                        .contentType(APPLICATION_JSON)
                        .content("{\"themeAccent\":\"HOTPINK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCENT"));

        // wall: member posts with glitter, body word-filtered, public read
        mockMvc.perform(post("/api/community/profiles/profile_owner/wall")
                        .header("Authorization", bearer(friend1))
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"damn cool page\",\"glitter\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("d*** cool page"))
                .andExpect(jsonPath("$.glitter").value(true));

        mockMvc.perform(get("/api/community/profiles/profile_owner/wall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author.username").value("profile_friend1"));

        // anonymous cannot post to a wall
        mockMvc.perform(post("/api/community/profiles/profile_owner/wall")
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"anon\"}"))
                .andExpect(status().is4xxClientError());
    }
}
