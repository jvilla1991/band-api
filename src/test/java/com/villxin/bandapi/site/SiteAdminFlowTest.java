package com.villxin.bandapi.site;

import com.villxin.bandapi.community.AbstractCommunityIntegrationTest;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.site.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the site controls: public flags/shows reads, admin
 * flag toggling, show CRUD, and the security boundary (anonymous and
 * non-admin callers are rejected from /api/site/admin/**).
 */
class SiteAdminFlowTest extends AbstractCommunityIntegrationTest {

    @Autowired
    private ShowRepository showRepository;

    private String adminBearer;

    @BeforeEach
    void setUpAdmin() {
        User admin = userRepository.findByEmail("site-admin@test.example").orElseGet(() -> {
            User user = new User();
            user.setEmail("site-admin@test.example");
            user.setRole("ADMIN");
            return userRepository.save(user);
        });
        adminBearer = bearer(admin);
        showRepository.deleteAll();
    }

    @Test
    void flagsSeedFromMigrationAndAreToggleable() throws Exception {
        mockMvc.perform(get("/api/site/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store").value(true))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.yourarea").value(false));

        mockMvc.perform(put("/api/site/admin/flags/live")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(true));

        mockMvc.perform(get("/api/site/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(true));

        // put it back so other tests see the seeded state
        mockMvc.perform(put("/api/site/admin/flags/live")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(false));
    }

    @Test
    void unknownFlagIsRejected() throws Exception {
        mockMvc.perform(put("/api/site/admin/flags/blog")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FLAG"));
    }

    @Test
    void adminEndpointsRejectAnonymousAndNonAdminCallers() throws Exception {
        mockMvc.perform(put("/api/site/admin/flags/store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        User fan = createMember("site_fan", false);
        mockMvc.perform(put("/api/site/admin/flags/store")
                        .header("Authorization", bearer(fan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/site/admin/shows").header("Authorization", bearer(fan)))
                .andExpect(status().isForbidden());
    }

    @Test
    void showCrudAndPublicListFiltering() throws Exception {
        String futureShow = """
                {"showDate":"%s","venue":"The Basement","city":"Nashville, TN",
                 "ticketUrl":"https://tickets.example/1","note":"with friends","status":"UPCOMING"}
                """.formatted(LocalDate.now().plusDays(30));
        String pastShow = """
                {"showDate":"%s","venue":"Old Haunt","city":"Memphis, TN"}
                """.formatted(LocalDate.now().minusDays(10));

        String created = mockMvc.perform(post("/api/site/admin/shows")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureShow))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.venue").value("The Basement"))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/site/admin/shows")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pastShow))
                .andExpect(status().isCreated());

        // public list hides past shows; admin list has both
        mockMvc.perform(get("/api/site/shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].venue").value("The Basement"));
        mockMvc.perform(get("/api/site/admin/shows").header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(put("/api/site/admin/shows/" + id)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureShow.replace("UPCOMING", "SOLD_OUT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD_OUT"));

        mockMvc.perform(delete("/api/site/admin/shows/" + id).header("Authorization", adminBearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/site/shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/site/admin/shows")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"venue\":\"No Date Club\"}"))
                .andExpect(status().isBadRequest());
    }
}
