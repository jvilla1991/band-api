package com.villxin.bandapi.site.controller;

import com.villxin.bandapi.site.dto.PhotoResponse;
import com.villxin.bandapi.site.entity.AboutPhoto;
import com.villxin.bandapi.site.service.AboutPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Public About carousel: the ordered photo list and the raw image bytes. Both
 * are public GETs (SecurityConfig permits {@code GET /api/site/**}).
 */
@RestController
@RequestMapping("/api/site/photos")
public class SitePhotoController {

    private final AboutPhotoService photoService;

    public SitePhotoController(AboutPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public List<PhotoResponse> list() {
        return photoService.list();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        AboutPhoto photo = photoService.getOrThrow(id);
        MediaType contentType = resolveMediaType(photo.getContentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .eTag("\"photo-" + photo.getId() + "-" + photo.getUpdatedAt().toEpochMilli() + "\"")
                .body(photo.getImageData());
    }

    private MediaType resolveMediaType(String stored) {
        try {
            return MediaType.parseMediaType(stored);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
