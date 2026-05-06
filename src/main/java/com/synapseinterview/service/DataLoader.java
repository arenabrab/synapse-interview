package com.synapseinterview.service;

import com.synapseinterview.model.Product;
import com.synapseinterview.model.Supplier;
import com.synapseinterview.repository.ProductRepository;
import com.synapseinterview.repository.SupplierRepository;
import com.synapseinterview.util.ZipMatcher;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class DataLoader implements ApplicationRunner {

    private final Resource productsCsv;
    private final Resource suppliersCsv;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public DataLoader(
            @Value("${app.data.products-csv}") Resource productsCsv,
            @Value("${app.data.suppliers-csv}") Resource suppliersCsv,
            ProductRepository productRepository,
            SupplierRepository supplierRepository) {
        this.productsCsv = productsCsv;
        this.suppliersCsv = suppliersCsv;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        loadProducts();
        loadSuppliers();
    }

    private void loadProducts() throws IOException {
        try (var reader = new InputStreamReader(productsCsv.getInputStream());
             var parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            parser.stream()
                    .map(r -> new Product(r.get("product_code"), r.get("product_name"), r.get("category")))
                    .forEach(productRepository::save);
        }
    }

    private void loadSuppliers() throws IOException {
        try (var reader = new InputStreamReader(suppliersCsv.getInputStream());
             var parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            parser.stream()
                    .map(this::recordToSupplier)
                    .forEach(supplierRepository::save);
        }
    }

    private Supplier recordToSupplier(CSVRecord r) {
        var serviceZips = ZipMatcher.parse(r.get("service_zips"));
        var categories = Arrays.stream(r.get("product_categories").split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
        var rawScore = r.get("customer_satisfaction_score");
        var score = rawScore.matches("\\d+") ? Integer.parseInt(rawScore) : null;
        var canMailOrder = "y".equalsIgnoreCase(r.get("can_mail_order?").trim());
        return new Supplier(
                r.get("supplier_id"),
                r.get("suplier_name"),   // note: CSV header has one-'p' typo
                serviceZips,
                categories,
                score,
                canMailOrder
        );
    }
}