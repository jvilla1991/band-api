package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.shop.entity.OrderItem;
import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductVariant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Live Printify shipping quotes, and the fallback that keeps checkout alive when they fail. */
class ShippingQuoteServiceTest {

    private static final long FALLBACK = 599L;

    private final PrintifyClient printifyClient = mock(PrintifyClient.class);
    private final ShippingQuoteService service = new ShippingQuoteService(printifyClient, FALLBACK);

    private static OrderItem item(String printifyProductId, long printifyVariantId, int quantity) {
        Product product = new Product();
        product.setPrintifyProductId(printifyProductId);
        ProductVariant variant = new ProductVariant();
        variant.setPrintifyVariantId(printifyVariantId);
        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setVariant(variant);
        orderItem.setQuantity(quantity);
        return orderItem;
    }

    @Test
    void usesPrintifyRateAndForwardsTheCartsLineItems() {
        when(printifyClient.calculateShippingCents(anyList(), any())).thenReturn(2549);

        long cents = service.quoteCents(List.of(item("pp-vneck", 42L, 2)));

        assertEquals(2549L, cents); // the real rate, not the flat fallback
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrintifyClient.LineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(printifyClient).calculateShippingCents(captor.capture(), any());
        PrintifyClient.LineItem sent = captor.getValue().get(0);
        assertEquals("pp-vneck", sent.productId());
        assertEquals(42L, sent.variantId());
        assertEquals(2, sent.quantity()); // quantity carries through, so multi-item carts price correctly
    }

    @Test
    void fallsBackToFlatRateWhenPrintifyThrows() {
        when(printifyClient.calculateShippingCents(anyList(), any()))
                .thenThrow(new RuntimeException("printify down"));

        assertEquals(FALLBACK, service.quoteCents(List.of(item("pp1", 1L, 1))));
    }

    @Test
    void fallsBackToFlatRateWhenPrintifyReturnsNoUsableRate() {
        when(printifyClient.calculateShippingCents(anyList(), any())).thenReturn(null);
        assertEquals(FALLBACK, service.quoteCents(List.of(item("pp1", 1L, 1))));

        when(printifyClient.calculateShippingCents(anyList(), any())).thenReturn(0);
        assertEquals(FALLBACK, service.quoteCents(List.of(item("pp1", 1L, 1))));
    }
}
