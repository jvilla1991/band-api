package com.villxin.bandapi.community.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Full-replace profile update (PUT semantics): omitted/null text fields are
 * cleared, omitted booleans default to false, omitted accent defaults to EMBER.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 2000) String avatarUrl,
        @Size(max = 4000) String about,
        @Size(max = 4000) String whoToMeet,
        @Size(max = 100) String mood,
        @Size(max = 200) String profileSong,
        String themeAccent,
        Boolean themeGlitter,
        Boolean themeTiledBg,
        @Size(max = 8, message = "Top friends is limited to 8") List<String> topFriends
) {}
