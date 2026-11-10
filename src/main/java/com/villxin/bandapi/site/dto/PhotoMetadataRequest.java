package com.villxin.bandapi.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body for PUT /api/site/admin/photos/{id} — metadata only, no image
 * replacement. {@code focus} is optional (CSS object-position or null).
 */
public record PhotoMetadataRequest(
        @NotBlank @Size(max = 500) String alt,
        @Size(max = 100) String focus,
        @NotNull Integer sortOrder
) {
}
