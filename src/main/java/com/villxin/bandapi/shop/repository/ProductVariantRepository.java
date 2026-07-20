package com.villxin.bandapi.shop.repository;

import com.villxin.bandapi.shop.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductId(Long productId);
    List<ProductVariant> findByProductIdAndActiveTrueOrderByPositionAsc(Long productId);
}
