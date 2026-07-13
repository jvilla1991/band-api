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

    /** Upserts one product and its variants; returns how many rows this transitioned to inactive. */
    private int syncProduct(PrintifyClient.PrintifyProduct remoteProduct) {
        int deactivated = 0;

        Product product = productRepository.findByPrintifyProductId(remoteProduct.id())
                .orElseGet(Product::new);
        boolean wasActive = product.isActive();

        List<PrintifyClient.PrintifyVariant> enabledVariants = remoteProduct.variants() == null
                ? List.of()
                : remoteProduct.variants().stream().filter(PrintifyClient.PrintifyVariant::enabled).toList();

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
            variant.setLabel(remoteVariant.title());
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
