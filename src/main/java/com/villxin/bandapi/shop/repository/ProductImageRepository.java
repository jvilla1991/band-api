package com.villxin.bandapi.shop.repository;

import com.villxin.bandapi.shop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);

    /**
     * Bulk delete, not a derived {@code deleteBy}: derived deletes load the
     * entities and queue removals that flush <em>after</em> the sync's new
     * inserts — this JPQL delete hits the database immediately, keeping the
     * delete-then-reinsert order the sync relies on.
     */
    @Modifying
    @Query("delete from ProductImage i where i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
