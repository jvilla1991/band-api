package com.villxin.bandapi.site.dto;

import com.villxin.bandapi.site.entity.AboutPhoto;

/**
 * Public/admin list shape for an About carousel photo. {@code imageUrl} is a
 * relative path to the bytes endpoint; the frontend joins it to API_BASE.
 */
public record PhotoResponse(
        Long id,
        String alt,
        String focus,
        int sortOrder,
        String imageUrl
) {
    public static PhotoResponse from(AboutPhoto photo) {
        return new PhotoResponse(
                photo.getId(),
                photo.getAlt(),
                photo.getFocus(),
                photo.getSortOrder(),
                "/api/site/photos/" + photo.getId() + "/image"
        );
    }
}
