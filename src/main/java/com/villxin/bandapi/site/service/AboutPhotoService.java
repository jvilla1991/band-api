package com.villxin.bandapi.site.service;

import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.site.dto.PhotoMetadataRequest;
import com.villxin.bandapi.site.dto.PhotoResponse;
import com.villxin.bandapi.site.entity.AboutPhoto;
import com.villxin.bandapi.site.repository.AboutPhotoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Business logic for the About carousel: list, serve bytes, upload, edit, delete. */
@Service
public class AboutPhotoService {

    private final AboutPhotoRepository repository;

    public AboutPhotoService(AboutPhotoRepository repository) {
        this.repository = repository;
    }

    public List<PhotoResponse> list() {
        return repository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(PhotoResponse::from)
                .toList();
    }

    /** The raw entity (with bytes) for the image endpoint, or 404 if missing. */
    public AboutPhoto getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND",
                        "No photo with id " + id));
    }

    /** Stores an uploaded image, appended to the end of the carousel. */
    public PhotoResponse create(MultipartFile file, String alt, String focus) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "An image file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_AN_IMAGE",
                    "Uploaded file must be an image (got content type: " + contentType + ")");
        }
        if (alt == null || alt.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALT_REQUIRED", "Alt text is required");
        }

        AboutPhoto photo = new AboutPhoto();
        photo.setAlt(alt.trim());
        photo.setFocus(focus == null || focus.isBlank() ? null : focus.trim());
        photo.setContentType(contentType);
        photo.setSortOrder(nextSortOrder());
        try {
            photo.setImageData(file.getBytes());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE",
                    "Could not read the uploaded file");
        }
        return PhotoResponse.from(repository.save(photo));
    }

    /** Updates metadata only (alt, focus, sort order) — never the image bytes. */
    public PhotoResponse update(Long id, PhotoMetadataRequest request) {
        AboutPhoto photo = getOrThrow(id);
        photo.setAlt(request.alt().trim());
        photo.setFocus(request.focus() == null || request.focus().isBlank() ? null : request.focus().trim());
        photo.setSortOrder(request.sortOrder());
        return PhotoResponse.from(repository.save(photo));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "No photo with id " + id);
        }
        repository.deleteById(id);
    }

    private int nextSortOrder() {
        return repository.findFirstByOrderBySortOrderDesc()
                .map(p -> p.getSortOrder() + 1)
                .orElse(0);
    }
}
