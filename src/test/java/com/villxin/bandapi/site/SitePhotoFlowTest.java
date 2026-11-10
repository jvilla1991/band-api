package com.villxin.bandapi.site;

import com.villxin.bandapi.community.AbstractCommunityIntegrationTest;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.site.entity.AboutPhoto;
import com.villxin.bandapi.site.repository.AboutPhotoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the About carousel: the startup seed (three photos),
 * public list + image-bytes reads, admin multipart upload / metadata edit /
 * delete, and the security boundary on /api/site/admin/photos.
 *
 * <p>The three seeded rows are created once at context startup and shared across
 * test classes, so this test never deletes them — it snapshots their ids and an
 * {@code @AfterEach} removes only rows it added, leaving the seed state intact.</p>
 */
class SitePhotoFlowTest extends AbstractCommunityIntegrationTest {

    @Autowired
    private AboutPhotoRepository photoRepository;

    private String adminBearer;
    private Set<Long> seedIds;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail("photo-admin@test.example").orElseGet(() -> {
            User user = new User();
            user.setEmail("photo-admin@test.example");
            user.setRole("ADMIN");
            return userRepository.save(user);
        });
        adminBearer = bearer(admin);
        seedIds = photoRepository.findAll().stream().map(AboutPhoto::getId).collect(Collectors.toSet());
    }

    @AfterEach
    void cleanUpAddedRows() {
        List<AboutPhoto> added = photoRepository.findAll().stream()
                .filter(p -> !seedIds.contains(p.getId()))
                .toList();
        photoRepository.deleteAll(added);
    }

    @Test
    void publicListReturnsSeededPhotosInOrder() throws Exception {
        mockMvc.perform(get("/api/site/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].alt").value("villxin seated under a graffitied bridge"))
                .andExpect(jsonPath("$[0].focus").doesNotExist())
                .andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[0].imageUrl").value(matchesPattern("/api/site/photos/\\d+/image")))
                .andExpect(jsonPath("$[2].alt").value("villxin in front of a stone tower"))
                .andExpect(jsonPath("$[2].focus").value("center 62%"));
    }

    @Test
    void imageEndpointReturnsBytesWithContentType() throws Exception {
        Long firstId = photoRepository.findAllByOrderBySortOrderAsc().get(0).getId();
        MvcResult result = mockMvc.perform(get("/api/site/photos/" + firstId + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().exists("Cache-Control"))
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(body.length > 1000, "expected real JPEG bytes");
        // JPEG magic number
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0xFF, body[0]);
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0xD8, body[1]);
    }

    @Test
    void imageEndpointReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/site/photos/999999/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
    }

    @Test
    void adminUploadEditDeleteRoundTrip() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "new.png", "image/png", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        String created = mockMvc.perform(multipart("/api/site/admin/photos")
                        .file(file)
                        .param("alt", "a brand new photo")
                        .param("focus", "top left")
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alt").value("a brand new photo"))
                .andExpect(jsonPath("$.focus").value("top left"))
                .andExpect(jsonPath("$.sortOrder").value(greaterThanOrEqualTo(3)))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // bytes are retrievable with the stored content type
        mockMvc.perform(get("/api/site/photos/" + id + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // metadata-only update
        mockMvc.perform(put("/api/site/admin/photos/" + id)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alt\":\"edited alt\",\"focus\":null,\"sortOrder\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alt").value("edited alt"))
                .andExpect(jsonPath("$.focus").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").value(9));

        mockMvc.perform(delete("/api/site/admin/photos/" + id).header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/site/photos/" + id + "/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRejectsNonImageFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/site/admin/photos")
                        .file(file)
                        .param("alt", "text file")
                        .header("Authorization", adminBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_AN_IMAGE"));
    }

    @Test
    void adminEndpointsRejectAnonymousAndNonAdminCallers() throws Exception {
        mockMvc.perform(get("/api/site/admin/photos"))
                .andExpect(status().isForbidden());

        User fan = createMember("photo_fan", false);
        mockMvc.perform(get("/api/site/admin/photos").header("Authorization", bearer(fan)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/site/admin/photos/1").header("Authorization", bearer(fan)))
                .andExpect(status().isForbidden());
    }
}
