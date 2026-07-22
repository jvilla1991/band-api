package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Releases Printify's publish locks so the owner can edit prices or delete
 * products again.
 *
 * <p>Background: the shop is a custom integration, so pressing Publish in the
 * Printify dashboard freezes the product ("publishing in progress") until an
 * integration completes the handshake. Nothing subscribes to those events, so
 * the freeze is permanent without this. Publishing is irrelevant to the site —
 * {@link ProductSyncService} mirrors the catalog regardless — so releasing a
 * lock costs nothing and unblocks catalog edits.</p>
 */
@Service
public class PublishLockService {

    private static final Logger log = LoggerFactory.getLogger(PublishLockService.class);

    private final PrintifyClient printifyClient;
    private final String storeUrl;

    public PublishLockService(PrintifyClient printifyClient,
                              @Value("${shop.store-url}") String storeUrl) {
        this.printifyClient = printifyClient;
        this.storeUrl = storeUrl;
    }

    /**
     * @param released products whose lock was lifted
     * @param alreadyUnlocked products that needed nothing
     * @param failures human-readable "title: reason" per product Printify refused
     */
    public record ReleaseResult(int released, int alreadyUnlocked, List<String> failures) {}

    public ReleaseResult releaseAll() {
        List<PrintifyClient.PrintifyProduct> products;
        try {
            products = printifyClient.listProducts();
        } catch (RestClientResponseException e) {
            throw translate(e);
        }

        int released = 0;
        int alreadyUnlocked = 0;
        List<String> failures = new ArrayList<>();

        for (PrintifyClient.PrintifyProduct product : products) {
            if (!product.locked()) {
                alreadyUnlocked++;
                continue;
            }
            try {
                printifyClient.markPublishingSucceeded(product.id(), storeUrl);
                released++;
                log.info("Released Printify publish lock: {} ({})", product.title(), product.id());
            } catch (RestClientResponseException e) {
                // A missing scope fails every product identically — surface it as
                // the actionable error instead of a list of identical failures.
                if (isAuthFailure(e)) {
                    throw translate(e);
                }
                log.warn("Could not release lock on {} ({}): {}", product.title(), product.id(), e.getStatusText());
                failures.add(product.title() + ": " + e.getStatusText());
            }
        }
        return new ReleaseResult(released, alreadyUnlocked, failures);
    }

    private boolean isAuthFailure(RestClientResponseException e) {
        return e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403;
    }

    private ApiException translate(RestClientResponseException e) {
        if (isAuthFailure(e)) {
            return new ApiException(HttpStatus.BAD_GATEWAY, "PRINTIFY_SCOPE_MISSING",
                    "Printify rejected the request. Releasing locks needs an API token with the "
                            + "products.write scope — the current token is read-only for products.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "PRINTIFY_ERROR",
                "Printify returned " + e.getStatusCode().value() + " (" + e.getStatusText() + ")");
    }
}
