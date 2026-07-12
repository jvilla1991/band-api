package com.villxin.bandapi.community.controller;

import com.villxin.bandapi.community.dto.CommunityDtos.ProfileDto;
import com.villxin.bandapi.community.dto.CommunityDtos.WallCommentDto;
import com.villxin.bandapi.community.dto.UpdateProfileRequest;
import com.villxin.bandapi.community.entity.WallComment;
import com.villxin.bandapi.community.repository.WallCommentRepository;
import com.villxin.bandapi.community.service.CommunityUserService;
import com.villxin.bandapi.community.service.ProfileService;
import com.villxin.bandapi.community.service.WordFilterService;
import com.villxin.bandapi.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public profile reads + own-profile edits + profile walls. */
@RestController
@RequestMapping("/api/community/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final WallCommentRepository wallCommentRepository;
    private final CommunityUserService users;
    private final WordFilterService wordFilter;

    public ProfileController(ProfileService profileService,
                             WallCommentRepository wallCommentRepository,
                             CommunityUserService users,
                             WordFilterService wordFilter) {
        this.profileService = profileService;
        this.wallCommentRepository = wallCommentRepository;
        this.users = users;
        this.wordFilter = wordFilter;
    }

    @GetMapping("/{username}")
    public ProfileDto get(@PathVariable String username) {
        return profileService.getProfile(username);
    }

    @PutMapping("/me")
    public ProfileDto updateOwn(@Valid @RequestBody UpdateProfileRequest request,
                                Authentication auth) {
        User user = users.requireMember(auth);
        return profileService.update(user, request);
    }

    @GetMapping("/{username}/wall")
    public List<WallCommentDto> wall(@PathVariable String username) {
        User profileUser = users.requireByUsername(username);
        return wallCommentRepository.findByProfileUserIdOrderByCreatedAtDesc(profileUser.getId())
                .stream().map(WallCommentDto::from).toList();
    }

    @PostMapping("/{username}/wall")
    public ResponseEntity<WallCommentDto> postToWall(@PathVariable String username,
                                                     @Valid @RequestBody WallCommentRequest request,
                                                     Authentication auth) {
        User author = users.requireMember(auth);
        User profileUser = users.requireByUsername(username);

        WallComment comment = new WallComment();
        comment.setProfileUser(profileUser);
        comment.setAuthor(author);
        comment.setBody(wordFilter.filter(request.body()));
        comment.setGlitter(Boolean.TRUE.equals(request.glitter()));
        return ResponseEntity.ok(WallCommentDto.from(wallCommentRepository.save(comment)));
    }

    record WallCommentRequest(
            @NotBlank @Size(max = 2000) String body,
            Boolean glitter
    ) {}
}
