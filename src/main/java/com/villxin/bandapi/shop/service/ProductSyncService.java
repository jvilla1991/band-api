package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductVariant;
import com.villxin.bandapi.shop.repository.ProductRepository;
import com.villxin.bandapi.shop.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pulls the Printify catalog and upserts it into Postgres. Printify is the
 * single source of truth for products, prices, and mockup images — nothing
 * in the catalog is editable through our own API anymore (see
 * {@code ProductController}, which is read/deactivate-only).
 */
@Service
public class ProductSyncService {

    private final PrintifyClient printifyClient;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public ProductSyncService(PrintifyClient printifyClient,
                              ProductRepository productRepository,
                              ProductVariantRepository variantRepository) {
        this.printifyClient = printifyClient;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public SyncResult sync() {
        List<PrintifyClient.PrintifyProduct> remoteProducts = printifyClient.listProducts();
        Set<String> remoteIds = new HashSet<>();
        int synced = 0;
        int deactivated = 0;

        for (PrintifyClient.PrintifyProduct remoteProduct : remoteProducts) {
            remoteIds.add(remoteProduct.id());
            deactivated += syncProduct(remoteProduct);
            synced++;
        }

        // Anything we have that Printify no longer returns at all (deleted shop-side).
        for (Product product : productRepository.findAll()) {
            if (product.getPrintifyProductId() != null
                    && !remoteIds.contains(product.getPrintifyProductId())
                    && product.isActive()) {
                product.setActive(false);
                productRepository.save(product);
                deactivated++;
            }
        }

        return new SyncResult(synced, deactivated);
    }

    /**
     * A variant's resolved size/color option values plus their canonical indexes
     * (the order Printify lists the option's values in, e.g. XS before S before M).
     */
    private record VariantDimensions(String size, int sizeIndex, String color, int colorIndex) {}

    /** Upserts one product and its variants; returns how many rows this transitioned to inactive. */
    private int syncProduct(PrintifyClient.PrintifyProduct remoteProduct) {
        int deactivated = 0;

        Product product = productRepository.findByPrintifyProductId(remoteProduct.id())
                .orElseGet(Product::new);
        boolean wasActive = product.isActive();

        Map<Long, VariantDimensions> dimensionsByVariantId = resolveDimensions(remoteProduct);
        List<PrintifyClient.PrintifyVariant> enabledVariants = remoteProduct.variants() == null
                ? List.of()
                : remoteProduct.variants().stream()
                        .filter(PrintifyClient.PrintifyVariant::enabled)
                        // canonical order: size first (XS…3XL per Printify's option order), then color
                        .sorted(Comparator
                                .comparingInt((PrintifyClient.PrintifyVariant v) ->
                                        dimensionsByVariantId.get(v.id()).sizeIndex())
                                .thenComparingInt(v -> dimensionsByVariantId.get(v.id()).colorIndex()))
                        .toList();

        product.setPrintifyProductId(remoteProduct.id());
        product.setName(remoteProduct.title());
        product.setDescription(remoteProduct.description());
        product.setImageUrl(resolveImageUrl(remoteProduct.images()));
        product.setPrice(minPrice(enabledVariants));
        product.setActive(remoteProduct.visible());
        product = productRepository.save(product);

        if (wasActive && !product.isActive()) {
            deactivated++;
        }

        Map<Long, ProductVariant> existingByPrintifyId = new HashMap<>();
        for (ProductVariant variant : variantRepository.findByProductId(product.getId())) {
            existingByPrintifyId.put(variant.getPrintifyVariantId(), variant);
        }

        Set<Long> keep = new HashSet<>();
        int position = 0;
        for (PrintifyClient.PrintifyVariant remoteVariant : enabledVariants) {
            ProductVariant variant = existingByPrintifyId.get(remoteVariant.id());
            if (variant == null) {
                variant = new ProductVariant();
                variant.setProduct(product);
                variant.setPrintifyVariantId(remoteVariant.id());
            }
            VariantDimensions dims = dimensionsByVariantId.get(remoteVariant.id());
            variant.setLabel(remoteVariant.title());
            variant.setSizeLabel(dims.size());
            variant.setColorLabel(dims.color());
            variant.setPrice(centsToDollars(remoteVariant.price()));
            variant.setPosition(position++);
            variant.setActive(true);
            variantRepository.save(variant);
            keep.add(remoteVariant.id());
        }

        // Variants that are no longer enabled/present get deactivated, not deleted
        // (existing order_items still reference them).
        for (ProductVariant existing : existingByPrintifyId.values()) {
            if (!keep.contains(existing.getPrintifyVariantId()) && existing.isActive()) {
                existing.setActive(false);
                variantRepository.save(existing);
                deactivated++;
            }
        }

        return deactivated;
    }

    /**
     * Maps every variant id to its size/color option titles and their canonical
     * indexes. Printify option value ids are unique within a product, so variants
     * reference values by id alone — no positional assumptions needed. Dimensions
     * other than size/color (shape, surface, …) stay in the combined label only.
     */
    private Map<Long, VariantDimensions> resolveDimensions(PrintifyClient.PrintifyProduct remoteProduct) {
        record ValueRef(String type, String title, int index) {}
        Map<Long, ValueRef> valuesById = new HashMap<>();
        if (remoteProduct.options() != null) {
            for (PrintifyClient.PrintifyOption option : remoteProduct.options()) {
                if (option.values() == null) continue;
                for (int i = 0; i < option.values().size(); i++) {
                    PrintifyClient.PrintifyOptionValue value = option.values().get(i);
                    valuesById.put(value.id(), new ValueRef(option.type(), value.title(), i));
                }
            }
        }

        Map<Long, VariantDimensions> result = new HashMap<>();
        if (remoteProduct.variants() == null) return result;
        for (PrintifyClient.PrintifyVariant variant : remoteProduct.variants()) {
            String size = null;
            String color = null;
            int sizeIndex = Integer.MAX_VALUE;
            int colorIndex = Integer.MAX_VALUE;
            if (variant.options() != null) {
                for (Long valueId : variant.options()) {
                    ValueRef ref = valuesById.get(valueId);
                    if (ref == null) continue;
                    if ("size".equals(ref.type())) {
                        size = ref.title();
                        sizeIndex = ref.index();
                    } else if ("color".equals(ref.type())) {
                        color = ref.title();
                        colorIndex = ref.index();
                    }
                }
            }
            result.put(variant.id(), new VariantDimensions(size, sizeIndex, color, colorIndex));
        }
        return result;
    }

    private String resolveImageUrl(List<PrintifyClient.PrintifyImage> images) {
        if (images == null || images.isEmpty()) return null;
        return images.stream()
                .filter(PrintifyClient.PrintifyImage::isDefault)
                .map(PrintifyClient.PrintifyImage::src)
                .findFirst()
                .orElse(images.get(0).src());
    }

    private BigDecimal minPrice(List<PrintifyClient.PrintifyVariant> enabledVariants) {
        return enabledVariants.stream()
                .map(v -> centsToDollars(v.price()))
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal centsToDollars(int cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2);
    }

    public record SyncResult(int synced, int deactivated) {}
}
