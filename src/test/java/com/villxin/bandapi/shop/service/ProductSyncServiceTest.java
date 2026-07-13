package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductVariant;
import com.villxin.bandapi.shop.repository.ProductRepository;
import com.villxin.bandapi.shop.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Printify DTO -> entity mapping, incl. deactivation, with PrintifyClient mocked out. */
class ProductSyncServiceTest {

    private final PrintifyClient printifyClient = mock(PrintifyClient.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final ProductSyncService syncService =
            new ProductSyncService(printifyClient, productRepository, variantRepository);

    @BeforeEach
    void stubSaves() {
        // save() returns what it's given, assigning an id the first time (mimics an identity PK)
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsNewProductWithEnabledVariantsOnlyInArrayOrder() {
        var printifyProduct = new PrintifyClient.PrintifyProduct(
                "pp1", "Ashfall Tee", "<p>desc</p>", true,
                List.of(
                        new PrintifyClient.PrintifyVariant(11L, "S", 2800, true),
                        new PrintifyClient.PrintifyVariant(12L, "M", 2800, true),
                        new PrintifyClient.PrintifyVariant(13L, "XL", 3200, false)), // disabled — skipped
                List.of(
                        new PrintifyClient.PrintifyImage("https://example.com/back.png", false),
                        new PrintifyClient.PrintifyImage("https://example.com/front.png", true)));
        when(printifyClient.listProducts()).thenReturn(List.of(printifyProduct));
        when(productRepository.findByPrintifyProductId("pp1")).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(variantRepository.findByProductId(1L)).thenReturn(List.of());

        ProductSyncService.SyncResult result = syncService.sync();

        assertEquals(1, result.synced());
        assertEquals(0, result.deactivated());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertEquals("Ashfall Tee", saved.getName());
        assertEquals("pp1", saved.getPrintifyProductId());
        assertEquals("https://example.com/front.png", saved.getImageUrl()); // is_default wins over array order
        assertEquals(0, saved.getPrice().compareTo(new BigDecimal("28.00"))); // min of enabled variants, cents/100
        assertTrue(saved.isActive());

        ArgumentCaptor<ProductVariant> variantCaptor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository, times(2)).save(variantCaptor.capture());
        List<ProductVariant> savedVariants = variantCaptor.getAllValues();
        assertEquals(2, savedVariants.size()); // the disabled XL variant never gets created
        assertEquals("S", savedVariants.get(0).getLabel());
        assertEquals(0, savedVariants.get(0).getPosition());
        assertEquals("M", savedVariants.get(1).getLabel());
        assertEquals(1, savedVariants.get(1).getPosition());
    }

    @Test
    void deactivatesVariantsNoLongerEnabledAndProductsNoLongerVisible() {
        Product existingProduct = new Product();
        ReflectionTestUtils.setField(existingProduct, "id", 5L);
        existingProduct.setPrintifyProductId("pp1");
        existingProduct.setActive(true);

        ProductVariant staleVariant = new ProductVariant();
        ReflectionTestUtils.setField(staleVariant, "id", 90L);
        staleVariant.setProduct(existingProduct);
        staleVariant.setPrintifyVariantId(99L);
        staleVariant.setActive(true);

        var printifyProduct = new PrintifyClient.PrintifyProduct(
                "pp1", "Ashfall Tee", "desc", false, // no longer visible
                List.of(), // no enabled variants this time
                List.of());
        when(printifyClient.listProducts()).thenReturn(List.of(printifyProduct));
        when(productRepository.findByPrintifyProductId("pp1")).thenReturn(Optional.of(existingProduct));
        when(productRepository.findAll()).thenReturn(List.of(existingProduct));
        when(variantRepository.findByProductId(5L)).thenReturn(List.of(staleVariant));

        ProductSyncService.SyncResult result = syncService.sync();

        assertEquals(1, result.synced());
        assertEquals(2, result.deactivated()); // the product itself + the stale variant
        assertFalse(existingProduct.isActive());
        assertFalse(staleVariant.isActive());
    }

    @Test
    void deactivatesProductNoLongerReturnedByPrintifyAtAll() {
        Product goneProduct = new Product();
        ReflectionTestUtils.setField(goneProduct, "id", 7L);
        goneProduct.setPrintifyProductId("pp-deleted");
        goneProduct.setActive(true);

        when(printifyClient.listProducts()).thenReturn(List.of()); // Printify no longer returns it at all
        when(productRepository.findAll()).thenReturn(List.of(goneProduct));

        ProductSyncService.SyncResult result = syncService.sync();

        assertEquals(0, result.synced());
        assertEquals(1, result.deactivated());
        assertFalse(goneProduct.isActive());
    }
}
