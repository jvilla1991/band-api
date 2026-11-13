package com.villxin.bandapi.mailinglist;

import com.villxin.bandapi.mailinglist.MailchimpSyncService.Status;
import com.villxin.bandapi.mailinglist.MailchimpSyncService.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only mailing-list controls (secured in SecurityConfig).
 *
 * <ul>
 *   <li>{@code GET  /status} — is Mailchimp on, and how many subscribers are in each state.
 *       Check this before the legacy import: the free plan holds 250 contacts.</li>
 *   <li>{@code POST /sync} — retry everything PENDING.</li>
 *   <li>{@code POST /sync?includeLegacy=true} — also import the pre-Mailchimp list,
 *       tagged {@value MailchimpSyncService#LEGACY_TAG}. Safe to repeat.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/mailing-list/admin")
public class MailingListAdminController {

    private final MailchimpSyncService syncService;

    public MailingListAdminController(MailchimpSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/status")
    public ResponseEntity<Status> status() {
        return ResponseEntity.ok(syncService.status());
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> sync(@RequestParam(defaultValue = "false") boolean includeLegacy) {
        return ResponseEntity.ok(syncService.syncPending(includeLegacy));
    }
}
