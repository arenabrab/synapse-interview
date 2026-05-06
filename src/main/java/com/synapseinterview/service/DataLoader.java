package com.synapseinterview.service;

import com.synapseinterview.model.Product;
import com.synapseinterview.model.Supplier;
import com.synapseinterview.model.ZipRange;
import com.synapseinterview.repository.ProductRepository;
import com.synapseinterview.repository.SupplierRepository;
import com.synapseinterview.util.ZipMatcher;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
        try (Reader reader = new InputStreamReader(productsCsv.getInputStream());
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                productRepository.save(new Product(
                        record.get("product_code"),
                        record.get("product_name"),
                        record.get("category")
                ));
            }
        }
    }

    private void loadSuppliers() throws IOException {
        try (Reader reader = new InputStreamReader(suppliersCsv.getInputStream());
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                List<ZipRange> serviceZips = ZipMatcher.parse(record.get("service_zips"));
                Set<String> categories = Arrays.stream(record.get("product_categories").split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());
                String rawScore = record.get("customer_satisfaction_score");
                Integer score = rawScore.matches("\\d+") ? Integer.parseInt(rawScore) : null;
                boolean canMailOrder = "y".equalsIgnoreCase(record.get("can_mail_order?").trim());

                supplierRepository.save(new Supplier(
                        record.get("supplier_id"),
                        record.get("suplier_name"),   // note: CSV header has one-'p' typo
                        serviceZips,
                        categories,
                        score,
                        canMailOrder
                ));
            }
        }
    }
}
