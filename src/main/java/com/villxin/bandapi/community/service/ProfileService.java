package com.villxin.bandapi.community.service;

import com.villxin.bandapi.community.dto.CommunityDtos.ProfileDto;
import com.villxin.bandapi.community.dto.CommunityDtos.UserSummaryDto;
import com.villxin.bandapi.community.dto.UpdateProfileRequest;
import com.villxin.bandapi.community.entity.ThemeAccent;
import com.villxin.bandapi.community.entity.TopFriend;
import com.villxin.bandapi.community.repository.TopFriendRepository;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final TopFriendRepository topFriendRepository;
    private final CommunityUserService users;

    public ProfileService(UserRepository userRepository,
                          TopFriendRepository topFriendRepository,
                          CommunityUserService users) {
        this.userRepository = userRepository;
        this.topFriendRepository = topFriendRepository;
        this.users = users;
    }

    public ProfileDto getProfile(String username) {
        User user = users.requireByUsername(username);
        return toDto(user);
    }

    public ProfileDto toDto(User user) {
        List<UserSummaryDto> topFriends = topFriendRepository
                .findByUserIdOrderByPositionAsc(user.getId())
                .stream().map(tf -> UserSummaryDto.from(tf.getFriend())).toList();
        return ProfileDto.from(user, topFriends);
    }

    /** Full-replace update of the caller's own profile. Official flag is never touched here. */
    @Transactional
    public ProfileDto update(User user, UpdateProfileRequest request) {
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? user.getUsername() : request.displayName().trim());
        user.setAvatarUrl(request.avatarUrl());
        user.setAbout(request.about());
        user.setWhoToMeet(request.whoToMeet());
        user.setMood(request.mood());
        user.setProfileSong(request.profileSong());
        user.setThemeAccent(parseAccent(request.themeAccent()));
        user.setThemeGlitter(Boolean.TRUE.equals(request.themeGlitter()));
        user.setThemeTiledBg(Boolean.TRUE.equals(request.themeTiledBg()));
        userRepository.save(user);

        replaceTopFriends(user, request.topFriends());
        return toDto(user);
    }

    private ThemeAccent parseAccent(String accent) {
        if (accent == null || accent.isBlank()) return ThemeAccent.EMBER;
        try {
            return ThemeAccent.valueOf(accent.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_ACCENT",
                    "Accent must be one of: EMBER, VIOLET, MOSS, GOLD, ICE");
        }
    }

    /** Replaces the Top 8 list. Duplicates and self-references are dropped silently. */
    private void replaceTopFriends(User user, List<String> usernames) {
        topFriendRepository.deleteByUserId(user.getId());
        if (usernames == null || usernames.isEmpty()) return;
        if (usernames.size() > 8) {
            throw ApiException.badRequest("TOP8_LIMIT", "Top friends is limited to 8");
        }

        Set<String> seen = new LinkedHashSet<>();
        List<TopFriend> slots = new ArrayList<>();
        int position = 0;
        for (String name : usernames) {
            String normalized = name == null ? "" : name.toLowerCase().trim();
            if (normalized.isEmpty() || normalized.equals(user.getUsername()) || !seen.add(normalized)) {
                continue;
            }
            User friend = users.requireByUsername(normalized); // 404 if unknown
            TopFriend slot = new TopFriend();
            slot.setUser(user);
            slot.setFriend(friend);
            slot.setPosition(position++);
            slots.add(slot);
        }
        topFriendRepository.saveAll(slots);
    }
}
