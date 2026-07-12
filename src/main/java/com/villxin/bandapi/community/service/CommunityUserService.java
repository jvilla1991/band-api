package com.villxin.bandapi.community.service;

import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Resolves the authenticated JWT principal (email) to a community member. */
@Service
public class CommunityUserService {

    private final UserRepository userRepository;

    public CommunityUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * The current authenticated user, required to be a community member
     * (i.e. has a claimed username — the legacy admin account does not).
     */
    public User requireMember(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        }
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unknown account"));
        if (user.getUsername() == null) {
            throw ApiException.forbidden("NOT_A_MEMBER", "This account is not a YourArea member");
        }
        return user;
    }

    /** A member looked up by username, or 404. */
    public User requireByUsername(String username) {
        return userRepository.findByUsername(username == null ? null : username.toLowerCase().trim())
                .orElseThrow(() -> ApiException.notFound("No member named " + username));
    }
}
