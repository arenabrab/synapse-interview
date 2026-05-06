# Lombok + Error Handling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Lombok annotations to eliminate constructor boilerplate and add structured logging, then add a `GlobalExceptionHandler` to return a consistent failure response for malformed or missing request bodies.

**Architecture:** Task 1 is a pure refactor — no behavior change, existing 21 tests serve as the regression guard. Task 2 is new behavior — tests are written first (TDD), then the handler is implemented. Both tasks touch only production code and test code; no YAML or build changes needed (Lombok is already on the classpath).

**Tech Stack:** Java 25, Spring Boot 4.0.6, Lombok, Gradle 9.4.1, JUnit 5, MockMvc. Spring Boot 4 / Jackson 3 import notes: `tools.jackson.databind.ObjectMapper`, `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `@MockitoBean` (not `@MockBean`).

---

### Task 1: Apply Lombok to all four production classes

**Files:**
- Modify: `src/main/java/com/synapseinterview/util/ZipMatcher.java`
- Modify: `src/main/java/com/synapseinterview/controller/OrderController.java`
- Modify: `src/main/java/com/synapseinterview/service/OrderRoutingService.java`
- Modify: `src/main/java/com/synapseinterview/service/DataLoader.java`

**Step 1: Apply all four file changes**

Use PowerShell `Set-Content` for each file (see CLAUDE.md — bash heredocs corrupt backslash escapes on Windows).

**`ZipMatcher.java`** — add `@UtilityClass`, remove the private no-arg constructor, drop `static` from all method declarations (Lombok makes them static):

```java
package com.synapseinterview.util;

import com.synapseinterview.model.ZipRange;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;

@UtilityClass
public class ZipMatcher {

    /**
     * Parses a raw service_zips CSV field into a list of ZipRange.
     * Supports discrete zips ("11410, 11419"), single ranges ("11232-11305"),
     * and multiple ranges ("2164-2213, 2143-2193").
     */
    public List<ZipRange> parse(String rawServiceZips) {
        return Arrays.stream(rawServiceZips.split(","))
                .map(String::trim)
                .map(ZipMatcher::parseToken)
                .toList();
    }

    private ZipRange parseToken(String token) {
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
    public boolean matches(List<ZipRange> ranges, String customerZip) {
        var zipInt = Integer.parseInt(customerZip);
        return ranges.stream().anyMatch(r -> r.contains(zipInt));
    }
}
```

**`OrderController.java`** — add `@RequiredArgsConstructor` + `@Slf4j`, remove explicit constructor:

```java
package com.synapseinterview.controller;

import com.synapseinterview.model.OrderRequest;
import com.synapseinterview.model.RoutingResponse;
import com.synapseinterview.service.OrderRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRoutingService routingService;

    @PostMapping("/route")
    public ResponseEntity<RoutingResponse> route(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(routingService.route(request));
    }
}
```

**`OrderRoutingService.java`** — add `@RequiredArgsConstructor` + `@Slf4j`, remove explicit constructor, add log statements:

```java
package com.synapseinterview.service;

import com.synapseinterview.model.*;
import com.synapseinterview.repository.ProductRepository;
import com.synapseinterview.repository.SupplierRepository;
import com.synapseinterview.util.ZipMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderRoutingService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public RoutingResponse route(OrderRequest request) {
        log.debug("Routing order: {}", request.orderId());

        var validationErrors = validate(request);
        if (!validationErrors.isEmpty()) {
            log.info("Order {} infeasible: {} validation error(s)", request.orderId(), validationErrors.size());
            return RoutingResponse.failure(validationErrors);
        }

        var eligibleByProduct = new LinkedHashMap<String, List<Supplier>>();
        var productCache = new LinkedHashMap<String, Product>();
        for (var item : request.items()) {
            var product = productRepository.findById(item.productCode());
            if (product.isEmpty()) {
                log.info("Order {} infeasible: unknown product {}", request.orderId(), item.productCode());
                return RoutingResponse.failure("Unknown product code: " + item.productCode());
            }
            var eligible = supplierRepository.findEligible(
                    product.get().category(), request.customerZip(), request.mailOrder());
            if (eligible.isEmpty()) {
                log.info("Order {} infeasible: no eligible supplier for {}", request.orderId(), item.productCode());
                return RoutingResponse.failure(
                        "No eligible supplier for product: " + item.productCode());
            }
            eligibleByProduct.put(item.productCode(), eligible);
            productCache.put(item.productCode(), product.get());
        }

        var itemsByCode = request.items().stream()
                .collect(Collectors.toMap(OrderItem::productCode, i -> i));

        var routing = greedyRoute(eligibleByProduct, productCache, itemsByCode, request.customerZip());
        log.info("Order {} routed to {} supplier(s)", request.orderId(), routing.size());
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
            coverage.get(best).forEach(remaining::remove);
        }

        return assignments;
    }
}
```

**`DataLoader.java`** — add `@Slf4j`, keep explicit constructor, add `log.info` after each load:

```java
package com.synapseinterview.service;

import com.synapseinterview.model.Product;
import com.synapseinterview.model.Supplier;
import com.synapseinterview.repository.ProductRepository;
import com.synapseinterview.repository.SupplierRepository;
import com.synapseinterview.util.ZipMatcher;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
        log.info("Loaded {} products", productRepository.findAll().size());
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
        log.info("Loaded {} suppliers", supplierRepository.findAll().size());
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

**Step 2: Run the full test suite**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, all 21 tests pass.

If compilation fails with a Lombok error on `ZipMatcher`, it means `@UtilityClass` conflicted with the explicit `static` keywords. Fix by adding `static` back to the method declarations:
```java
public static List<ZipRange> parse(...) { ... }
private static ZipRange parseToken(...) { ... }
public static boolean matches(...) { ... }
```

**Step 3: Commit**

```
git add src/main/java/com/synapseinterview/util/ZipMatcher.java
git add src/main/java/com/synapseinterview/controller/OrderController.java
git add src/main/java/com/synapseinterview/service/OrderRoutingService.java
git add src/main/java/com/synapseinterview/service/DataLoader.java
git commit -m "refactor: add Lombok (@UtilityClass, @RequiredArgsConstructor, @Slf4j) and structured logging"
```

---

### Task 2: GlobalExceptionHandler + tests

**Files:**
- Create: `src/main/java/com/synapseinterview/controller/GlobalExceptionHandler.java`
- Create: `src/test/java/com/synapseinterview/controller/GlobalExceptionHandlerTest.java`

**Step 1: Write the failing tests first**

Create `src/test/java/com/synapseinterview/controller/GlobalExceptionHandlerTest.java`:

```java
package com.synapseinterview.controller;

import com.synapseinterview.service.OrderRoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderRoutingService routingService;

    @Test
    void missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Request body is missing or malformed."));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Request body is missing or malformed."));
    }

    @Test
    void wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Content-Type must be application/json."));
    }
}
```

**Step 2: Run tests to verify they fail**

```
./gradlew test --tests "com.synapseinterview.controller.GlobalExceptionHandlerTest"
```

Expected: FAIL — compilation error (class `GlobalExceptionHandler` does not exist yet) or test failures.

**Step 3: Create `GlobalExceptionHandler.java`**

```java
package com.synapseinterview.controller;

import com.synapseinterview.model.RoutingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RoutingResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(RoutingResponse.failure("Request body is missing or malformed."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RoutingResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.debug("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(RoutingResponse.failure("Content-Type must be application/json."));
    }
}
```

**Step 4: Run the full test suite**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, all 24 tests pass (21 existing + 3 new).

**Step 5: Commit**

```
git add src/main/java/com/synapseinterview/controller/GlobalExceptionHandler.java
git add src/test/java/com/synapseinterview/controller/GlobalExceptionHandlerTest.java
git commit -m "feat: add GlobalExceptionHandler for malformed and missing request bodies"
```
