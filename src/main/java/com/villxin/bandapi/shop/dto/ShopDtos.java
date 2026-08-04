package com.villxin.bandapi.shop.dto;

import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.entity.ProductImage;
import com.villxin.bandapi.shop.entity.ProductVariant;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTOs for the shop's public product API — entities are never
 * serialized directly.
 */
public final class ShopDtos {

    private ShopDtos() {}

    public record VariantDto(Long id, Long printifyId, String label, String size, String color,
                             BigDecimal price) {
        public static VariantDto from(ProductVariant v) {
            return new VariantDto(v.getId(), v.getPrintifyVariantId(), v.getLabel(), v.getSizeLabel(),
                    v.getColorLabel(), v.getPrice());
        }
    }

    /** One mockup shot; variantIds (Printify ids) say which variants it depicts. */
    public record ProductImageDto(String src, boolean isDefault, List<Long> variantIds) {
        public static ProductImageDto from(ProductImage i) {
            return new ProductImageDto(i.getSrc(), i.isDefault(), i.getVariantIds());
        }
    }

    public record ProductDto(Long id, String name, String description, BigDecimal price,
                             String imageUrl, List<ProductImageDto> images, List<VariantDto> variants) {
        public static ProductDto from(Product p, List<ProductImageDto> images, List<VariantDto> variants) {
            return new ProductDto(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getImageUrl(), images, variants);
        }
    }
}
