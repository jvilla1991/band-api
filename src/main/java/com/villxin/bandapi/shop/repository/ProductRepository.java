package com.villxin.bandapi.shop.repository;

import com.villxin.bandapi.shop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrueOrderByIdAsc();
    Optional<Product> findByPrintifyProductId(String printifyProductId);
}
