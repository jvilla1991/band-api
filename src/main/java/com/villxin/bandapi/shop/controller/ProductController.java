package com.villxin.bandapi.shop.controller;

import com.villxin.bandapi.shop.dto.ShopDtos.ProductDto;
import com.villxin.bandapi.shop.dto.ShopDtos.VariantDto;
import com.villxin.bandapi.shop.entity.Product;
import com.villxin.bandapi.shop.repository.ProductRepository;
import com.villxin.bandapi.shop.repository.ProductVariantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Read-only product catalog + admin deactivate. The catalog itself (name,
 * description, price, images, variants) is owned by Printify and populated
 * by {@code ProductSyncService} — there is no create/update here anymore.
 */
@RestController
@RequestMapping("/api/shop/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public ProductController(ProductRepository productRepository,
                             ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @GetMapping
    public List<ProductDto> list() {
        return productRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivate(@PathVariable Long id) {
        return productRepository.findById(id).map(product -> {
            product.setActive(false);
            productRepository.save(product);
            return ResponseEntity.ok(Map.of("message", "Product deactivated"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private ProductDto toDto(Product product) {
        List<VariantDto> variants = variantRepository
                .findByProductIdAndActiveTrueOrderByPositionAsc(product.getId())
                .stream().map(VariantDto::from).toList();
        return ProductDto.from(product, variants);
    }
}
