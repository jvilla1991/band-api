package com.villxin.bandapi.shop.controller;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.villxin.bandapi.shop.entity.Order;
import com.villxin.bandapi.shop.entity.OrderItem;
import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.repository.OrderRepository;
import com.villxin.bandapi.shop.repository.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shop")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public OrderController(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest request) throws Exception {
        Stripe.apiKey = stripeSecretKey;

        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : request.items()) {
            Product product = productRepository.findById(cartItem.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + cartItem.productId()));

            if (product.getStockQuantity() < cartItem.quantity()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Insufficient stock for: " + product.getName()));
            }

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(cartItem.quantity());
            item.setUnitPrice(product.getPrice());
            orderItems.add(item);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.quantity())));

            lineItems.add(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) cartItem.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("usd")
                            .setUnitAmount(product.getPrice().multiply(BigDecimal.valueOf(100)).longValue())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(product.getName())
                                    .build())
                            .build())
                    .build());
        }

        Order order = new Order();
        order.setCustomerEmail(request.customerEmail());
        order.setTotalAmount(total);
        orderItems.forEach(item -> {
            item.setOrder(order);
            order.getItems().add(item);
        });
        orderRepository.save(order);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(request.customerEmail())
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addAllLineItem(lineItems)
                .putMetadata("orderId", order.getId().toString())
                .build();

        Session session = Session.create(params);

        order.setStripeSessionId(session.getId());
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("url", session.getUrl()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow();

            orderRepository.findByStripeSessionId(session.getId()).ifPresent(order -> {
                order.setStatus(Order.Status.PAID);
                orderRepository.save(order);

                // Decrement stock
                order.getItems().forEach(item -> {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
                    productRepository.save(product);
                });
            });
        }

        return ResponseEntity.ok("received");
    }

    record CartItem(
        @Min(1) long productId,
        @Min(1) int quantity
    ) {}

    record CheckoutRequest(
        @Email @NotBlank String customerEmail,
        @NotEmpty List<CartItem> items
    ) {}
}
