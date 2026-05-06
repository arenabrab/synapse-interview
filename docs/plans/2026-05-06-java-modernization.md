# Java Modernization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Modernize all production Java source with `var`, stream pipelines, and `Collectors.toUnmodifiableSet()` — zero behavior change.

**Architecture:** Three files touched in isolation. Each task ends with a full test run to confirm no regressions. No new tests needed since no new behavior is introduced.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Gradle 9.4.1, JUnit 5.

---

### Task 1: Modernize `ZipMatcher`

**Files:**
- Modify: `src/main/java/com/synapseinterview/util/ZipMatcher.java`

**Step 1: Apply the changes**

Replace the entire file content:

```java
package com.synapseinterview.util;

import com.synapseinterview.model.ZipRange;

import java.util.Arrays;
import java.util.List;

public final class ZipMatcher {

    private ZipMatcher() {}

    /**
     * Parses a raw service_zips CSV field into a list of ZipRange.
     * Supports discrete zips ("11410, 11419"), single ranges ("11232-11305"),
     * and multiple ranges ("2164-2213, 2143-2193").
     */
    public static List<ZipRange> parse(String rawServiceZips) {
        return Arrays.stream(rawServiceZips.split(","))
                .map(String::trim)
                .map(ZipMatcher::parseToken)
                .toList();
    }

    private static ZipRange parseToken(String token) {
        if (token.contains("-")) {
            var parts = token.split("-", 2);
            var min = Integer.parseInt(parts[0].trim());
            var max = Integer.parseInt(parts[1].trim());
            return new ZipRange(min, max);
        }
        var zip = Integer.parseInt(token);
        return new ZipRange(zip, zip);
    }

    /**
     * Returns true if customerZip (parsed as integer) falls within any of the ranges.
     * Integer comparison handles leading-zero-stripped CSV values correctly.
     */
    public static boolean matches(List<ZipRange> ranges, String customerZip) {
        var zipInt = Integer.parseInt(customerZip);
        return ranges.stream().anyMatch(r -> r.contains(zipInt));
    }
}
```

Key changes:
- `parse()` replaced with `Arrays.stream(...).map(...).toList()` — removes `ArrayList` import
- `parseToken(String)` private helper holds the range/discrete branch logic
- `var` on all locals in `parseToken` and `matches`

**Step 2: Run tests**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, 21 tests pass.

**Step 3: Commit**

```
git add src/main/java/com/synapseinterview/util/ZipMatcher.java
git commit -m "refactor: modernize ZipMatcher with stream pipeline and var"
```

---

### Task 2: Modernize `DataLoader`

**Files:**
- Modify: `src/main/java/com/synapseinterview/service/DataLoader.java`

**Step 1: Apply the changes**

Replace the entire file content:

```java
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
import java.util.Arrays;
import java.util.List;
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
```

Key changes:
- `var` on `reader` and `parser` in both try-with-resources blocks
- `loadProducts`: for-loop replaced with `parser.stream().map(...).forEach(productRepository::save)`
- `loadSuppliers`: for-loop replaced with `parser.stream().map(this::recordToSupplier).forEach(...)`; body extracted to `recordToSupplier` private helper to keep the lambda readable
- `recordToSupplier`: `var` on all locals; `Collectors.toSet()` → `Collectors.toUnmodifiableSet()`
- Unused imports removed: `java.io.Reader`, `java.util.Set`

**Step 2: Run tests**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, 21 tests pass.

**Step 3: Commit**

```
git add src/main/java/com/synapseinterview/service/DataLoader.java
git commit -m "refactor: modernize DataLoader with stream pipelines, var, and toUnmodifiableSet"
```

---

### Task 3: Modernize `OrderRoutingService`

**Files:**
- Modify: `src/main/java/com/synapseinterview/service/OrderRoutingService.java`

**Step 1: Apply the changes**

Replace the entire file content:

```java
package com.synapseinterview.service;

import com.synapseinterview.model.*;
import com.synapseinterview.repository.ProductRepository;
import com.synapseinterview.repository.SupplierRepository;
import com.synapseinterview.util.ZipMatcher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderRoutingService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public OrderRoutingService(ProductRepository productRepository,
                               SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
    }

    public RoutingResponse route(OrderRequest request) {
        var validationErrors = validate(request);
        if (!validationErrors.isEmpty()) {
            return RoutingResponse.failure(validationErrors);
        }

        var eligibleByProduct = new LinkedHashMap<String, List<Supplier>>();
        var productCache = new LinkedHashMap<String, Product>();
        for (var item : request.items()) {
            var product = productRepository.findById(item.productCode());
            if (product.isEmpty()) {
                return RoutingResponse.failure("Unknown product code: " + item.productCode());
            }
            var eligible = supplierRepository.findEligible(
                    product.get().category(), request.customerZip(), request.mailOrder());
            if (eligible.isEmpty()) {
                return RoutingResponse.failure(
                        "No eligible supplier for product: " + item.productCode());
            }
            eligibleByProduct.put(item.productCode(), eligible);
            productCache.put(item.productCode(), product.get());
        }

        var itemsByCode = request.items().stream()
                .collect(Collectors.toMap(OrderItem::productCode, i -> i));

        var routing = greedyRoute(eligibleByProduct, productCache, itemsByCode, request.customerZip());
        return RoutingResponse.success(routing);
    }

    private List<String> validate(OrderRequest request) {
        var errors = new ArrayList<String>();
        if (request.items() == null || request.items().isEmpty()) {
            errors.add("Order must include at least one line item.");
        }
        if (request.customerZip() == null || !request.customerZip().matches("[0-9]{5}")) {
            errors.add("Order must include a valid customer_zip.");
        }
        return errors;
    }

    /**
     * Greedy set-cover: repeatedly picks the supplier covering the most
     * remaining items until all items are assigned.
     */
    private List<SupplierAssignment> greedyRoute(
            Map<String, List<Supplier>> eligibleByProduct,
            Map<String, Product> productCache,
            Map<String, OrderItem> itemsByCode,
            String customerZip) {

        var remaining = new LinkedHashSet<>(eligibleByProduct.keySet());
        var assignments = new ArrayList<SupplierAssignment>();

        while (!remaining.isEmpty()) {
            var coverage = new LinkedHashMap<Supplier, List<String>>();
            for (var productCode : remaining) {
                for (var supplier : eligibleByProduct.get(productCode)) {
                    coverage.computeIfAbsent(supplier, k -> new ArrayList<>()).add(productCode);
                }
            }

            var best = coverage.entrySet().stream()
                    .max(Comparator.comparingInt(e -> e.getValue().size()))
                    .map(Map.Entry::getKey)
                    .orElseThrow();

            var fulfillmentMode = ZipMatcher.matches(best.serviceZips(), customerZip)
                    ? "local" : "mail_order";

            var routedItems = coverage.get(best).stream()
                    .map(code -> new RoutedItem(
                            code,
                            itemsByCode.get(code).quantity(),
                            productCache.get(code).category(),
                            fulfillmentMode
                    ))
                    .toList();

            assignments.add(new SupplierAssignment(
                    best.supplierId(), best.supplierName(), routedItems));
            remaining.removeAll(coverage.get(best));
        }

        return assignments;
    }
}
```

Key changes:
- `var` on every local variable throughout `route`, `validate`, and `greedyRoute`
- Double `findById` eliminated: `productCache` is now populated inside the existing eligibility loop (saves one `findById` call per item); the second stream over `request.items()` for `productCache` is removed
- `for (var item :`, `for (var productCode :`, `for (var supplier :` in enhanced for-each loops

**Step 2: Run tests**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, 21 tests pass.

**Step 3: Commit**

```
git add src/main/java/com/synapseinterview/service/OrderRoutingService.java
git commit -m "refactor: modernize OrderRoutingService with var and fix double findById"
```
