package com.villxin.bandapi.shop.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Address;
import com.stripe.model.Event;
import com.stripe.model.ShippingDetails;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.shop.entity.Order;
import com.villxin.bandapi.shop.entity.OrderItem;
import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductVariant;
import com.villxin.bandapi.shop.repository.OrderRepository;
import com.villxin.bandapi.shop.repository.ProductVariantRepository;
import com.villxin.bandapi.shop.service.PrintifyClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/shop")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final PrintifyClient printifyClient;
    private final String webhookSecret;
    private final String successUrl;
    private final String cancelUrl;
    private final long shippingFlatCents;
    private final List<SessionCreateParams.ShippingAddressCollection.AllowedCountry> allowedCountries;

    public OrderController(OrderRepository orderRepository,
                           ProductVariantRepository variantRepository,
                           PrintifyClient printifyClient,
                           @Value("${stripe.secret-key}") String stripeSecretKey,
                           @Value("${stripe.webhook-secret}") String webhookSecret,
                           @Value("${stripe.success-url}") String successUrl,
                           @Value("${stripe.cancel-url}") String cancelUrl,
                           @Value("${shop.shipping-flat-cents}") long shippingFlatCents,
                           @Value("${shop.allowed-countries}") String allowedCountriesRaw) {
        this.orderRepository = orderRepository;
        this.variantRepository = variantRepository;
        this.printifyClient = printifyClient;
        this.webhookSecret = webhookSecret;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.shippingFlatCents = shippingFlatCents;
        this.allowedCountries = Arrays.stream(allowedCountriesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> SessionCreateParams.ShippingAddressCollection.AllowedCountry.valueOf(s.toUpperCase()))
                .toList();
        Stripe.apiKey = stripeSecretKey;
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest request) throws StripeException {
        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : request.items()) {
            ProductVariant variant = variantRepository.findById(cartItem.variantId())
                    .filter(ProductVariant::isActive)
                    .filter(v -> v.getProduct().isActive())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_VARIANT",
                            "Unknown or inactive variant: " + cartItem.variantId()));
            Product product = variant.getProduct();

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(cartItem.quantity());
            item.setUnitPrice(variant.getPrice());
            orderItems.add(item);

            total = total.add(variant.getPrice().multiply(BigDecimal.valueOf(cartItem.quantity())));

            SessionCreateParams.LineItem.PriceData.ProductData.Builder productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(product.getName() + " — " + variant.getLabel());
            if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
                productData.addImage(product.getImageUrl());
            }

            lineItems.add(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) cartItem.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("usd")
                            .setUnitAmount(variant.getPrice().multiply(BigDecimal.valueOf(100)).longValue())
                            .setProductData(productData.build())
                            .build())
                    .build());
        }

        Order order = new Order();
        order.setTotalAmount(total);
        orderItems.forEach(item -> {
            item.setOrder(order);
            order.getItems().add(item);
        });

        SessionCreateParams.ShippingAddressCollection.Builder shippingAddressCollection =
                SessionCreateParams.ShippingAddressCollection.builder();
        allowedCountries.forEach(shippingAddressCollection::addAllowedCountry);

        SessionCreateParams.ShippingOption shippingOption = SessionCreateParams.ShippingOption.builder()
                .setShippingRateData(SessionCreateParams.ShippingOption.ShippingRateData.builder()
                        .setType(SessionCreateParams.ShippingOption.ShippingRateData.Type.FIXED_AMOUNT)
                        .setDisplayName("Standard · 7–10 days production + transit")
                        .setFixedAmount(SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                .setAmount(shippingFlatCents)
                                .setCurrency("usd")
                                .build())
                        .build())
                .build();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .setShippingAddressCollection(shippingAddressCollection.build())
                .addShippingOption(shippingOption)
                .addAllLineItem(lineItems)
                .build();

        Session session = Session.create(params);
        order.setStripeSessionId(session.getId());
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("url", session.getUrl()));
    }

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String sigHeader) throws StripeException {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        if (!"checkout.session.completed".equals(event.getType())) {
            return ResponseEntity.ok("received");
        }

        Session session = event.getDataObjectDeserializer().getObject()
                .map(o -> (Session) o)
                .orElseGet(() -> retrieveSessionFromRawPayload(payload));

        return handleCompletedSession(session);
    }

    /**
     * {@code event.getDataObjectDeserializer().getObject()} comes back empty when the
     * webhook's API version doesn't match the SDK's — fall back to pulling the session
     * id out of the raw payload and re-fetching it directly.
     */
    private Session retrieveSessionFromRawPayload(String payload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            String sessionId = root.path("data").path("object").path("id").asText(null);
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalStateException("Webhook payload is missing data.object.id");
            }
            return Session.retrieve(sessionId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse webhook payload", e);
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to retrieve checkout session " + payload, e);
        }
    }

    /** Marks the order paid and hands it to Printify. Package-private for direct unit testing. */
    ResponseEntity<String> handleCompletedSession(Session session) {
        Optional<Order> maybeOrder = orderRepository.findByStripeSessionId(session.getId());
        if (maybeOrder.isEmpty()) {
            log.warn("Webhook for unknown Stripe session {}", session.getId());
            return ResponseEntity.ok("received");
        }
        Order order = maybeOrder.get();

        // Idempotent: Stripe retries webhooks, and a Printify failure below leaves
        // printifyOrderId null so a retry can pick this order back up.
        if (order.getStatus() == Order.Status.PAID && order.getPrintifyOrderId() != null) {
            return ResponseEntity.ok("received");
        }

        order.setStatus(Order.Status.PAID);
        if (session.getCustomerDetails() != null) {
            order.setCustomerEmail(session.getCustomerDetails().getEmail());
        }
        orderRepository.save(order);

        try {
            String printifyOrderId = printifyClient.createOrder(
                    order.getId(), buildLineItems(order), buildAddress(session));
            order.setPrintifyOrderId(printifyOrderId);
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("Printify order creation failed for order {}", order.getId(), e);
            return ResponseEntity.internalServerError().body("Printify order creation failed");
        }

        return ResponseEntity.ok("received");
    }

    List<PrintifyClient.LineItem> buildLineItems(Order order) {
        return order.getItems().stream()
                .map(item -> new PrintifyClient.LineItem(
                        item.getProduct().getPrintifyProductId(),
                        item.getVariant().getPrintifyVariantId(),
                        item.getQuantity()))
                .toList();
    }

    PrintifyClient.AddressTo buildAddress(Session session) {
        ShippingDetails shipping = session.getShippingDetails();
        Address address = shipping != null ? shipping.getAddress() : null;
        String[] name = splitName(shipping != null ? shipping.getName() : null);
        String email = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;

        return new PrintifyClient.AddressTo(
                name[0],
                name[1],
                email,
                "",
                address != null ? address.getCountry() : "",
                address != null ? address.getState() : "",
                address != null ? address.getLine1() : "",
                address != null ? address.getLine2() : "",
                address != null ? address.getCity() : "",
                address != null ? address.getPostalCode() : "");
    }

    /** Splits "First Last" on the last space; single-word names get "-" for a last name. */
    static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Customer", "-"};
        }
        String trimmed = fullName.trim();
        int idx = trimmed.lastIndexOf(' ');
        return idx < 0
                ? new String[]{trimmed, "-"}
                : new String[]{trimmed.substring(0, idx), trimmed.substring(idx + 1)};
    }

    record CartItem(
        @Min(1) long variantId,
        @Min(1) int quantity
    ) {}

    record CheckoutRequest(
        @NotEmpty List<CartItem> items
    ) {}
}
