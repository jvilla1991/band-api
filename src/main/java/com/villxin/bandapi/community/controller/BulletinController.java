package com.villxin.bandapi.community.controller;

import com.villxin.bandapi.community.dto.CommunityDtos.BulletinDto;
import com.villxin.bandapi.community.entity.Bulletin;
import com.villxin.bandapi.community.repository.BulletinRepository;
import com.villxin.bandapi.community.service.CommunityUserService;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Bulletins from the official villxin account, shown on the YourArea home. */
@RestController
@RequestMapping("/api/community/bulletins")
public class BulletinController {

    private final BulletinRepository bulletinRepository;
    private final CommunityUserService users;

    public BulletinController(BulletinRepository bulletinRepository, CommunityUserService users) {
        this.bulletinRepository = bulletinRepository;
        this.users = users;
    }

    @GetMapping
    public Page<BulletinDto> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return bulletinRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 50)))
                .map(BulletinDto::from);
    }

    /** Only the account flagged official in the DB may post. */
    @PostMapping
    public ResponseEntity<BulletinDto> create(@Valid @RequestBody BulletinRequest request,
                                              Authentication auth) {
        User author = users.requireMember(auth);
        if (!author.isOfficial()) {
            throw ApiException.forbidden("NOT_OFFICIAL", "Only the official villxin account can post bulletins");
        }
        Bulletin bulletin = new Bulletin();
        bulletin.setAuthor(author);
        bulletin.setTitle(request.title());
        bulletin.setBody(request.body());
        return ResponseEntity.ok(BulletinDto.from(bulletinRepository.save(bulletin)));
    }

    record BulletinRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String body
    ) {}
}
