package com.villxin.bandapi.shop.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * One Printify mockup image of a {@link Product} (front/back/folded/on-model).
 * Populated entirely by {@code ProductSyncService}, which deletes and reinserts
 * a product's rows on every sync — mockup URLs churn when Printify regenerates
 * them and nothing else references these rows, so there is no soft delete here.
 */
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String src;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    /** Index in Printify's images[] array — the intended display order. */
    @Column(nullable = false)
    private int position = 0;

    /** Printify variant ids this mockup depicts (mockups are per-color-group). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_ids", nullable = false)
    private List<Long> variantIds = new ArrayList<>();

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public List<Long> getVariantIds() { return variantIds; }
    public void setVariantIds(List<Long> variantIds) { this.variantIds = variantIds; }
}
