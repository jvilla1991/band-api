package com.villxin.bandapi.shop.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for the Printify API: catalog reads for {@link ProductSyncService}
 * and order creation for fulfillment. Printify orders come in "on hold" —
 * nothing here calls the send-to-production endpoint, so the owner approves
 * every order from the Printify dashboard before it actually prints.
 */
@Component
public class PrintifyClient {

    private static final String BASE_URL = "https://api.printify.com/v1";

    private final RestClient restClient;
    private final String shopId;

    public PrintifyClient(@Value("${printify.api-token}") String apiToken,
                          @Value("${printify.shop-id}") String shopId) {
        this.shopId = shopId;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                // Printify sits behind Cloudflare, which 403s (error 1010) generic
                // language-default user agents — identify ourselves explicitly.
                .defaultHeader("User-Agent", "villxin-band-api/1.0")
                .build();
    }

    /** Pulls every product in the shop, following pagination to the last page. */
    public List<PrintifyProduct> listProducts() {
        List<PrintifyProduct> all = new ArrayList<>();
        int page = 1;
        while (true) {
            PrintifyProductsPage response = restClient.get()
                    .uri("/shops/{shopId}/products.json?limit=50&page={page}", shopId, page)
                    .retrieve()
                    .body(PrintifyProductsPage.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }
            all.addAll(response.data());
            if (page >= response.lastPage()) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * What Printify will charge us to ship these line items, in cents (their
     * "standard" rate). US rates depend on the items and quantities, not the
     * destination — verified identical for TX/NY/CA/AK/HI/PR — so a quote taken
     * with any US address holds for whatever address the buyer enters later.
     */
    public Integer calculateShippingCents(List<LineItem> lineItems, AddressTo addressTo) {
        ShippingRequest body = new ShippingRequest(lineItems, addressTo);
        ShippingResponse response = restClient.post()
                .uri("/shops/{shopId}/orders/shipping.json", shopId)
                .body(body)
                .retrieve()
                .body(ShippingResponse.class);
        return response != null ? response.standard() : null;
    }

    /**
     * Creates an order in Printify, on hold for the owner's approval. Returns
     * the Printify order id to store alongside our own order.
     */
    public String createOrder(long ourOrderId, List<LineItem> lineItems, AddressTo addressTo) {
        CreateOrderRequest body = new CreateOrderRequest(
                "villxin-" + ourOrderId,
                "villxin order " + ourOrderId,
                lineItems,
                1,
                true,
                addressTo);

        PrintifyOrderResponse response = restClient.post()
                .uri("/shops/{shopId}/orders.json", shopId)
                .body(body)
                .retrieve()
                .body(PrintifyOrderResponse.class);

        return response != null ? response.id() : null;
    }

    /**
     * Completes the publish handshake for a product, which is what releases
     * Printify's edit/delete lock. Our shop is a "custom integration", so
     * pressing Publish in the Printify dashboard locks the product until an
     * integration reports back — and nothing here subscribes to publish events,
     * so without this call the lock never lifts. The catalog reaches the site
     * through {@link ProductSyncService} regardless of publish state; this just
     * tells Printify the listing exists so it stops waiting.
     */
    public void markPublishingSucceeded(String productId, String storeUrl) {
        restClient.post()
                .uri("/shops/{shopId}/products/{productId}/publishing_succeeded.json", shopId, productId)
                .body(new PublishingSucceeded(new ExternalListing(productId, storeUrl)))
                .retrieve()
                .toBodilessEntity();
    }

    // --- catalog DTOs (Printify -> us) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintifyProductsPage(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("last_page") int lastPage,
            List<PrintifyProduct> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintifyProduct(
            String id,
            String title,
            String description,
            boolean visible,
            /** True while Printify awaits a publish handshake — the product can't be edited or deleted. */
            @JsonProperty("is_locked") boolean locked,
            List<PrintifyOption> options,
            List<PrintifyVariant> variants,
            List<PrintifyImage> images) {}

    /** A product dimension (type "size", "color", "shape", …) with its values in canonical order. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintifyOption(
            String name,
            String type,
            List<PrintifyOptionValue> values) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintifyOptionValue(
            long id,
            String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintifyVariant(
            long id,
            String title,
            int price, // cents
            @JsonProperty("is_enabled") boolean enabled,
            /** Option value ids, one per product dimension (matched by id, not index). */
            List<Long> options) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintifyImage(
            String src,
            @JsonProperty("is_default") boolean isDefault) {}

    // --- order-creation DTOs (us -> Printify) ---

    public record LineItem(
            @JsonProperty("product_id") String productId,
            @JsonProperty("variant_id") long variantId,
            int quantity) {}

    public record AddressTo(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            String phone,
            String country,
            String region,
            String address1,
            String address2,
            String city,
            String zip) {}

    private record ShippingRequest(
            @JsonProperty("line_items") List<LineItem> lineItems,
            @JsonProperty("address_to") AddressTo addressTo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ShippingResponse(Integer standard, Integer express) {}

    private record CreateOrderRequest(
            @JsonProperty("external_id") String externalId,
            String label,
            @JsonProperty("line_items") List<LineItem> lineItems,
            @JsonProperty("shipping_method") int shippingMethod,
            @JsonProperty("send_shipping_notification") boolean sendShippingNotification,
            @JsonProperty("address_to") AddressTo addressTo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PrintifyOrderResponse(String id) {}

    // --- publish handshake DTOs (us -> Printify) ---

    private record PublishingSucceeded(ExternalListing external) {}

    /** Where the listing "lives" as far as Printify is concerned. */
    private record ExternalListing(String id, String handle) {}
}
