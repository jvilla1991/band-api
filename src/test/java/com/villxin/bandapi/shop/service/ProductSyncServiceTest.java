package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductImage;
import com.villxin.bandapi.shop.entity.ProductVariant;
import com.villxin.bandapi.shop.repository.ProductImageRepository;
import com.villxin.bandapi.shop.repository.ProductRepository;
import com.villxin.bandapi.shop.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Printify DTO -> entity mapping, incl. deactivation, with PrintifyClient mocked out. */
class ProductSyncServiceTest {

    private final PrintifyClient printifyClient = mock(PrintifyClient.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final ProductImageRepository imageRepository = mock(ProductImageRepository.class);
    private final ProductSyncService syncService =
            new ProductSyncService(printifyClient, productRepository, variantRepository, imageRepository);

    @BeforeEach
    void stubSaves() {
        // save() returns what it's given, assigning an id the first time (mimics an identity PK)
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsNewProductWithEnabledVariantsSortedBySizeThenColorWithDimensionLabels() {
        var sizeOption = new PrintifyClient.PrintifyOption("Sizes", "size", List.of(
                new PrintifyClient.PrintifyOptionValue(1L, "S"),
                new PrintifyClient.PrintifyOptionValue(2L, "M"),
                new PrintifyClient.PrintifyOptionValue(3L, "XL")));
        var colorOption = new PrintifyClient.PrintifyOption("Colors", "color", List.of(
                new PrintifyClient.PrintifyOptionValue(10L, "White"),
                new PrintifyClient.PrintifyOptionValue(11L, "Black")));
        var printifyProduct = new PrintifyClient.PrintifyProduct(
                "pp1", "Ashfall Tee", "<p>desc</p>", true, false,
                List.of(sizeOption, colorOption),
                List.of(
                        // deliberately out of order: Printify's variant array is not canonical
                        new PrintifyClient.PrintifyVariant(12L, "Black / M", 2800, true, List.of(2L, 11L)),
                        new PrintifyClient.PrintifyVariant(11L, "White / S", 2800, true, List.of(1L, 10L)),
                        new PrintifyClient.PrintifyVariant(14L, "White / M", 2800, true, List.of(2L, 10L)),
                        new PrintifyClient.PrintifyVariant(13L, "White / XL", 3200, false, List.of(3L, 10L))), // disabled
                List.of(
                        // mockups come per color group; the last one only depicts the disabled XL variant
                        new PrintifyClient.PrintifyImage("https://example.com/back.png", List.of(12L), "back", false),
                        new PrintifyClient.PrintifyImage("https://example.com/front.png", List.of(11L, 14L), "front", true),
                        new PrintifyClient.PrintifyImage("https://example.com/disabled-only.png", List.of(13L), "front", false)));
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
        verify(variantRepository, times(3)).save(variantCaptor.capture());
        List<ProductVariant> savedVariants = variantCaptor.getAllValues();
        assertEquals(3, savedVariants.size()); // the disabled XL variant never gets created

        // canonical order: S before M (size option order), White before Black within M
        assertEquals("White / S", savedVariants.get(0).getLabel());
        assertEquals("S", savedVariants.get(0).getSizeLabel());
        assertEquals("White", savedVariants.get(0).getColorLabel());
        assertEquals(0, savedVariants.get(0).getPosition());
        assertEquals("White / M", savedVariants.get(1).getLabel());
        assertEquals(1, savedVariants.get(1).getPosition());
        assertEquals("Black / M", savedVariants.get(2).getLabel());
        assertEquals("M", savedVariants.get(2).getSizeLabel());
        assertEquals("Black", savedVariants.get(2).getColorLabel());
        assertEquals(2, savedVariants.get(2).getPosition());

        // gallery: old rows dropped before the new ones go in
        InOrder imageOrder = inOrder(imageRepository);
        imageOrder.verify(imageRepository).deleteByProductId(1L);
        ArgumentCaptor<ProductImage> imageCaptor = ArgumentCaptor.forClass(ProductImage.class);
        imageOrder.verify(imageRepository, times(2)).save(imageCaptor.capture());
        List<ProductImage> savedImages = imageCaptor.getAllValues();
        // the disabled-only mockup is skipped; array order and variant tags survive
        assertEquals("https://example.com/back.png", savedImages.get(0).getSrc());
        assertEquals(List.of(12L), savedImages.get(0).getVariantIds());
        assertEquals(0, savedImages.get(0).getPosition());
        assertFalse(savedImages.get(0).isDefault());
        assertEquals("https://example.com/front.png", savedImages.get(1).getSrc());
        assertEquals(List.of(11L, 14L), savedImages.get(1).getVariantIds());
        assertEquals(1, savedImages.get(1).getPosition());
        assertTrue(savedImages.get(1).isDefault());
    }

    @Test
    void cardImageSkipsDefaultMockupThatOnlyDepictsDisabledVariants() {
        var printifyProduct = new PrintifyClient.PrintifyProduct(
                "pp1", "Ashfall Tee", "desc", true, false,
                List.of(),
                List.of(
                        new PrintifyClient.PrintifyVariant(21L, "Black / M", 2800, true, List.of()),
                        new PrintifyClient.PrintifyVariant(22L, "White / M", 2800, false, List.of())),
                List.of(
                        // Printify's default depicts only the disabled (white) variant
                        new PrintifyClient.PrintifyImage("https://example.com/white.png", List.of(22L), "front", true),
                        new PrintifyClient.PrintifyImage("https://example.com/black.png", List.of(21L), "front", false)));
        when(printifyClient.listProducts()).thenReturn(List.of(printifyProduct));
        when(productRepository.findByPrintifyProductId("pp1")).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of());
        when(variantRepository.findByProductId(1L)).thenReturn(List.of());

        syncService.sync();

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals("https://example.com/black.png", productCaptor.getValue().getImageUrl());

        // and the white-only mockup never reaches the gallery
        ArgumentCaptor<ProductImage> imageCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(imageCaptor.capture());
        assertEquals("https://example.com/black.png", imageCaptor.getValue().getSrc());
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
                "pp1", "Ashfall Tee", "desc", false, false, // no longer visible
                List.of(),
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
