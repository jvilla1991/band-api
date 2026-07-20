package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.shop.entity.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Prices shipping for a cart using Printify's real rates.
 *
 * <p>Stripe Checkout fixes its shipping options when the session is created —
 * before the buyer types an address — while Printify prices per destination.
 * That is only a conflict in theory: Printify's US rate is driven by the items
 * and quantities, not the destination (verified identical for TX/NY/CA/AK/HI/PR),
 * so we quote with a representative US address and the number holds. Revisit
 * this if the shop ever ships outside the US.
 *
 * <p>A flat fallback keeps checkout working when Printify is unreachable —
 * selling at a possibly-wrong shipping price beats not selling at all.
 */
@Service
public class ShippingQuoteService {

    private static final Logger log = LoggerFactory.getLogger(ShippingQuoteService.class);

    /** Any valid US address quotes the same rate; the buyer's real one is collected by Stripe. */
    private static final PrintifyClient.AddressTo QUOTE_ADDRESS = new PrintifyClient.AddressTo(
            "Shipping", "Quote", "quote@villxin.com", "",
            "US", "TX", "1 Test Street", "", "Austin", "78701");

    private final PrintifyClient printifyClient;
    private final long fallbackCents;

    public ShippingQuoteService(PrintifyClient printifyClient,
                                @Value("${shop.shipping-fallback-cents}") long fallbackCents) {
        this.printifyClient = printifyClient;
        this.fallbackCents = fallbackCents;
    }

    /** Shipping to charge for these items, in cents. Never throws — falls back instead. */
    public long quoteCents(List<OrderItem> items) {
        List<PrintifyClient.LineItem> lineItems = items.stream()
                .map(item -> new PrintifyClient.LineItem(
                        item.getProduct().getPrintifyProductId(),
                        item.getVariant().getPrintifyVariantId(),
                        item.getQuantity()))
                .toList();

        try {
            Integer standard = printifyClient.calculateShippingCents(lineItems, QUOTE_ADDRESS);
            if (standard == null || standard <= 0) {
                log.warn("Printify returned no standard shipping rate; using fallback {}c", fallbackCents);
                return fallbackCents;
            }
            return standard;
        } catch (Exception e) {
            log.warn("Printify shipping quote failed; using fallback {}c", fallbackCents, e);
            return fallbackCents;
        }
    }
}
