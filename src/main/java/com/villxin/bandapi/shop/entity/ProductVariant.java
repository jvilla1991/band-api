package com.villxin.bandapi.shop.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * A single Printify variant (e.g. a size or size/color combo) of a {@link Product}.
 * Populated entirely by {@code ProductSyncService} — never created via the API.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "printify_variant_id", nullable = false)
    private Long printifyVariantId;

    @Column(nullable = false, length = 100)
    private String label;

    /** Size option title from Printify (e.g. "M", "2XL"); null when the product has no size dimension. */
    @Column(name = "size_label", length = 100)
    private String sizeLabel;

    /** Color option title from Printify (e.g. "Solid Black"); null when the product has no color dimension. */
    @Column(name = "color_label", length = 100)
    private String colorLabel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int position = 0;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Long getPrintifyVariantId() { return printifyVariantId; }
    public void setPrintifyVariantId(Long printifyVariantId) { this.printifyVariantId = printifyVariantId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSizeLabel() { return sizeLabel; }
    public void setSizeLabel(String sizeLabel) { this.sizeLabel = sizeLabel; }
    public String getColorLabel() { return colorLabel; }
    public void setColorLabel(String colorLabel) { this.colorLabel = colorLabel; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
