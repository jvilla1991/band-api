package com.villxin.bandapi.shop.dto;

import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductVariant;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTOs for the shop's public product API — entities are never
 * serialized directly.
 */
public final class ShopDtos {

    private ShopDtos() {}

    public record VariantDto(Long id, String label, BigDecimal price) {
        public static VariantDto from(ProductVariant v) {
            return new VariantDto(v.getId(), v.getLabel(), v.getPrice());
        }
    }

    public record ProductDto(Long id, String name, String description, BigDecimal price,
                             String imageUrl, List<VariantDto> variants) {
        public static ProductDto from(Product p, List<VariantDto> variants) {
            return new ProductDto(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getImageUrl(), variants);
        }
    }
}
