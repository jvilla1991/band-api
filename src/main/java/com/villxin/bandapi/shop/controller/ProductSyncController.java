package com.villxin.bandapi.shop.controller;

import com.villxin.bandapi.shop.service.ProductSyncService;
import com.villxin.bandapi.shop.service.ProductSyncService.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin-triggered pull of the Printify catalog into Postgres. */
@RestController
@RequestMapping("/api/shop")
public class ProductSyncController {

    private final ProductSyncService productSyncService;

    public ProductSyncController(ProductSyncService productSyncService) {
        this.productSyncService = productSyncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> sync() {
        SyncResult result = productSyncService.sync();
        return ResponseEntity.ok(Map.of("synced", result.synced(), "deactivated", result.deactivated()));
    }
}
