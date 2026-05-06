package com.synapseinterview.repository;

import com.synapseinterview.model.Product;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductRepository {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public void save(Product product) {
        products.put(product.productCode(), product);
    }

    public Optional<Product> findById(String productCode) {
        return Optional.ofNullable(products.get(productCode));
    }

    public Collection<Product> findAll() {
        return products.values();
    }
}
