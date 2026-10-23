package com.ecommerce.wishlistservice.repository;

import com.ecommerce.wishlistservice.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {
}
