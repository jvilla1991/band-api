package com.villxin.bandapi.shop.controller;

import com.stripe.model.Address;
import com.stripe.model.ShippingDetails;
import com.stripe.model.checkout.Session;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.shop.entity.Order;
import com.villxin.bandapi.shop.entity.OrderItem;
import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductVariant;
import com.villxin.bandapi.shop.repository.OrderRepository;
import com.villxin.bandapi.shop.repository.ProductVariantRepository;
import com.villxin.bandapi.shop.service.PrintifyClient;
import com.villxin.bandapi.shop.service.ShippingQuoteService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit tests: checkout validation, and the webhook -> Printify
 * payload mapping (incl. the idempotency short-circuit). No Spring context,
 * no Stripe network calls — {@code Session}/{@code Address} are plain model
 * objects we build by hand, and the tested methods are package-private for
 * exactly this purpose.
 */
class OrderControllerTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final PrintifyClient printifyClient = mock(PrintifyClient.class);
    private final ShippingQuoteService shippingQuoteService = mock(ShippingQuoteService.class);

    private final OrderController controller = new OrderController(
            orderRepository, variantRepository, printifyClient, shippingQuoteService,
            "sk_test_dummy", "whsec_dummy",
            "http://localhost:5173/#/store/success", "http://localhost:5173/#/store",
            "US");

    // --- checkout validation ---

    @Test
    void checkoutRejectsUnknownVariant() {
        when(variantRepository.findById(99L)).thenReturn(Optional.empty());
        var request = new OrderController.CheckoutRequest(List.of(new OrderController.CartItem(99L, 1)));

        ApiException ex = assertThrows(ApiException.class, () -> controller.checkout(request));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void checkoutRejectsInactiveVariant() {
        ProductVariant inactive = new ProductVariant();
        inactive.setActive(false);
        when(variantRepository.findById(5L)).thenReturn(Optional.of(inactive));
        var request = new OrderController.CheckoutRequest(List.of(new OrderController.CartItem(5L, 1)));

        assertThrows(ApiException.class, () -> controller.checkout(request));
    }

    @Test
    void checkoutRejectsVariantWhoseProductIsInactive() {
        Product inactiveProduct = new Product();
        inactiveProduct.setActive(false);
        ProductVariant variant = new ProductVariant();
        variant.setActive(true);
        variant.setProduct(inactiveProduct);
        when(variantRepository.findById(7L)).thenReturn(Optional.of(variant));
        var request = new OrderController.CheckoutRequest(List.of(new OrderController.CartItem(7L, 1)));

        assertThrows(ApiException.class, () -> controller.checkout(request));
    }

    @Test
    void emptyItemsFailsBeanValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var request = new OrderController.CheckoutRequest(List.of());

        Set<ConstraintViolation<OrderController.CheckoutRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    // --- webhook -> Printify mapping ---

    @Test
    void completedSessionMarksOrderPaidAndCreatesPrintifyOrder() {
        Order order = orderWithOneItem();
        when(orderRepository.findByStripeSessionId("sess_1")).thenReturn(Optional.of(order));
        when(printifyClient.createOrder(anyLong(), anyList(), any())).thenReturn("printify-order-1");

        Session session = fakeSession("sess_1", "cust@example.com", "Jane Doe",
                "123 Main St", "Apt 4", "Springfield", "IL", "62704", "US");

        ResponseEntity<String> response = controller.handleCompletedSession(session);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Order.Status.PAID, order.getStatus());
        assertEquals("printify-order-1", order.getPrintifyOrderId());
        assertEquals("cust@example.com", order.getCustomerEmail());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrintifyClient.LineItem>> lineItemsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PrintifyClient.AddressTo> addressCaptor = ArgumentCaptor.forClass(PrintifyClient.AddressTo.class);
        verify(printifyClient).createOrder(eq(order.getId()), lineItemsCaptor.capture(), addressCaptor.capture());

        PrintifyClient.LineItem lineItem = lineItemsCaptor.getValue().get(0);
        assertEquals("pp1", lineItem.productId());
        assertEquals(11L, lineItem.variantId());
        assertEquals(2, lineItem.quantity());

        PrintifyClient.AddressTo address = addressCaptor.getValue();
        assertEquals("Jane", address.firstName());
        assertEquals("Doe", address.lastName());
        assertEquals("cust@example.com", address.email());
        assertEquals("123 Main St", address.address1());
        assertEquals("Apt 4", address.address2());
        assertEquals("Springfield", address.city());
        assertEquals("IL", address.region());
        assertEquals("62704", address.zip());
        assertEquals("US", address.country());
    }

    @Test
    void singleWordShippingNameDefaultsLastNameToDash() {
        Order order = orderWithOneItem();
        when(orderRepository.findByStripeSessionId("sess_2")).thenReturn(Optional.of(order));
        when(printifyClient.createOrder(anyLong(), anyList(), any())).thenReturn("printify-order-2");

        Session session = fakeSession("sess_2", "cust@example.com", "Cher",
                "1 Infinite Loop", "", "Cupertino", "CA", "95014", "US");

        controller.handleCompletedSession(session);

        ArgumentCaptor<PrintifyClient.AddressTo> addressCaptor = ArgumentCaptor.forClass(PrintifyClient.AddressTo.class);
        verify(printifyClient).createOrder(anyLong(), anyList(), addressCaptor.capture());
        assertEquals("Cher", addressCaptor.getValue().firstName());
        assertEquals("-", addressCaptor.getValue().lastName());
    }

    @Test
    void alreadyFulfilledOrderShortCircuitsWithoutCallingPrintify() {
        Order order = orderWithOneItem();
        order.setStatus(Order.Status.PAID);
        order.setPrintifyOrderId("printify-existing");
        when(orderRepository.findByStripeSessionId("sess_3")).thenReturn(Optional.of(order));

        Session session = fakeSession("sess_3", "cust@example.com", "Jane Doe",
                "123 Main St", "", "Springfield", "IL", "62704", "US");

        ResponseEntity<String> response = controller.handleCompletedSession(session);

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(printifyClient);
    }

    @Test
    void printifyFailureKeepsOrderPaidWithNullPrintifyIdAndReturns500() {
        Order order = orderWithOneItem();
        when(orderRepository.findByStripeSessionId("sess_4")).thenReturn(Optional.of(order));
        when(printifyClient.createOrder(anyLong(), anyList(), any()))
                .thenThrow(new RuntimeException("Printify is down"));

        Session session = fakeSession("sess_4", "cust@example.com", "Jane Doe",
                "123 Main St", "", "Springfield", "IL", "62704", "US");

        ResponseEntity<String> response = controller.handleCompletedSession(session);

        assertEquals(500, response.getStatusCode().value());
        assertEquals(Order.Status.PAID, order.getStatus());
        assertNull(order.getPrintifyOrderId());
    }

    // --- fixtures ---

    private Order orderWithOneItem() {
        Product product = new Product();
        product.setName("Ashfall Tee");
        product.setPrintifyProductId("pp1");

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setPrintifyVariantId(11L);
        variant.setLabel("M");
        variant.setPrice(new BigDecimal("28.00"));

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setVariant(variant);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("28.00"));

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 42L);
        item.setOrder(order);
        order.getItems().add(item);
        return order;
    }

    private Session fakeSession(String id, String email, String shippingName,
                               String line1, String line2, String city, String state,
                               String zip, String country) {
        Session session = new Session();
        session.setId(id);

        Session.CustomerDetails customerDetails = new Session.CustomerDetails();
        customerDetails.setEmail(email);
        session.setCustomerDetails(customerDetails);

        Address address = new Address();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(zip);
        address.setCountry(country);

        ShippingDetails shippingDetails = new ShippingDetails();
        shippingDetails.setName(shippingName);
        shippingDetails.setAddress(address);
        session.setShippingDetails(shippingDetails);

        return session;
    }
}
