package com.villxin.bandapi.community.controller;

import com.villxin.bandapi.community.dto.CommunityDtos.DmMessageDto;
import com.villxin.bandapi.community.dto.CommunityDtos.DmRequestDto;
import com.villxin.bandapi.community.dto.CommunityDtos.InboxThreadDto;
import com.villxin.bandapi.community.dto.CommunityDtos.SentFolderDto;
import com.villxin.bandapi.community.dto.CommunityDtos.ThreadMessagesDto;
import com.villxin.bandapi.community.service.CommunityUserService;
import com.villxin.bandapi.community.service.DmService;
import com.villxin.bandapi.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Request-gated DMs + Mail Center folders. All endpoints require auth. */
@RestController
@RequestMapping("/api/community/dms")
public class DmController {

    private final DmService dmService;
    private final CommunityUserService users;

    public DmController(DmService dmService, CommunityUserService users) {
        this.dmService = dmService;
        this.users = users;
    }

    // --- requests ---

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> sendRequest(@Valid @RequestBody SendRequestRequest request,
                                                           Authentication auth) {
        User sender = users.requireMember(auth);
        dmService.sendRequest(sender, request.toUsername(), request.message());
        // Always "sent" — a blocked sender must not be able to tell the difference.
        return ResponseEntity.ok(Map.of("ok", true, "message", "Message sent"));
    }

    @GetMapping("/requests")
    public List<DmRequestDto> incomingRequests(Authentication auth) {
        return dmService.incomingRequests(users.requireMember(auth));
    }

    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable Long id, Authentication auth) {
        Long threadId = dmService.accept(users.requireMember(auth), id);
        return ResponseEntity.ok(Map.of("ok", true, "threadId", threadId));
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(@PathVariable Long id, Authentication auth) {
        dmService.decline(users.requireMember(auth), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // --- folders ---

    @GetMapping("/inbox")
    public List<InboxThreadDto> inbox(Authentication auth) {
        return dmService.inbox(users.requireMember(auth));
    }

    @GetMapping("/sent")
    public SentFolderDto sent(Authentication auth) {
        return dmService.sentFolder(users.requireMember(auth));
    }

    @GetMapping("/trash")
    public List<InboxThreadDto> trash(Authentication auth) {
        return dmService.trashFolder(users.requireMember(auth));
    }

    // --- threads ---

    /** Also marks the thread read for the caller. */
    @GetMapping("/threads/{id}")
    public ThreadMessagesDto thread(@PathVariable Long id, Authentication auth) {
        return dmService.getThread(users.requireMember(auth), id);
    }

    @PostMapping("/threads/{id}/messages")
    public ResponseEntity<DmMessageDto> message(@PathVariable Long id,
                                                @Valid @RequestBody MessageRequest request,
                                                Authentication auth) {
        return ResponseEntity.ok(dmService.postMessage(users.requireMember(auth), id, request.body()));
    }

    /** "Revoke access" — closes the thread both ends (block). */
    @PostMapping("/threads/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(@PathVariable Long id, Authentication auth) {
        dmService.revoke(users.requireMember(auth), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Re-accept — only the revoker can reopen. */
    @PostMapping("/threads/{id}/reopen")
    public ResponseEntity<Map<String, Object>> reopen(@PathVariable Long id, Authentication auth) {
        dmService.reopen(users.requireMember(auth), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/threads/{id}/trash")
    public ResponseEntity<Map<String, Object>> trashThread(@PathVariable Long id, Authentication auth) {
        dmService.setTrashed(users.requireMember(auth), id, true);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/threads/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreThread(@PathVariable Long id, Authentication auth) {
        dmService.setTrashed(users.requireMember(auth), id, false);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    record SendRequestRequest(
            @NotBlank String toUsername,
            @NotBlank @Size(max = 5000) String message
    ) {}

    record MessageRequest(@NotBlank @Size(max = 5000) String body) {}
}
