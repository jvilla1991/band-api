package com.villxin.bandapi.site.controller;

import com.villxin.bandapi.site.dto.PhotoMetadataRequest;
import com.villxin.bandapi.site.dto.PhotoResponse;
import com.villxin.bandapi.site.service.AboutPhotoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Owner-only About carousel management (ADMIN role, enforced in SecurityConfig
 * via {@code /api/site/admin/**}). Upload is multipart; metadata edits are JSON.
 */
@RestController
@RequestMapping("/api/site/admin/photos")
public class SitePhotoAdminController {

    private final AboutPhotoService photoService;

    public SitePhotoAdminController(AboutPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public List<PhotoResponse> list() {
        return photoService.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponse> create(@RequestParam("file") MultipartFile file,
                                                @RequestParam("alt") String alt,
                                                @RequestParam(value = "focus", required = false) String focus) {
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.create(file, alt, focus));
    }

    @PutMapping("/{id}")
    public PhotoResponse update(@PathVariable Long id, @Valid @RequestBody PhotoMetadataRequest request) {
        return photoService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        photoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
