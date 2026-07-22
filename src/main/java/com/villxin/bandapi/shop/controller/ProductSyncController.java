package com.villxin.bandapi.shop.controller;

import com.villxin.bandapi.shop.service.ProductSyncService;
import com.villxin.bandapi.shop.service.ProductSyncService.SyncResult;
import com.villxin.bandapi.shop.service.PublishLockService;
import com.villxin.bandapi.shop.service.PublishLockService.ReleaseResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin-triggered Printify catalog operations: pull into Postgres, release publish locks. */
@RestController
@RequestMapping("/api/shop")
public class ProductSyncController {

    private final ProductSyncService productSyncService;
    private final PublishLockService publishLockService;

    public ProductSyncController(ProductSyncService productSyncService, PublishLockService publishLockService) {
        this.productSyncService = productSyncService;
        this.publishLockService = publishLockService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> sync() {
        SyncResult result = productSyncService.sync();
        return ResponseEntity.ok(Map.of("synced", result.synced(), "deactivated", result.deactivated()));
    }

    /** Unfreezes products Printify locked mid-publish so the owner can edit or delete them. */
    @PostMapping("/release-locks")
    public ResponseEntity<ReleaseResult> releaseLocks() {
        return ResponseEntity.ok(publishLockService.releaseAll());
    }
}
