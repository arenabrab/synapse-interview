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
        List<String> validationErrors = validate(request);
        if (!validationErrors.isEmpty()) {
            return RoutingResponse.failure(validationErrors);
        }

        Map<String, List<Supplier>> eligibleByProduct = new LinkedHashMap<>();
        for (OrderItem item : request.items()) {
            Optional<Product> product = productRepository.findById(item.productCode());
            if (product.isEmpty()) {
                return RoutingResponse.failure("Unknown product code: " + item.productCode());
            }
            List<Supplier> eligible = supplierRepository.findEligible(
                    product.get().category(), request.customerZip(), request.mailOrder());
            if (eligible.isEmpty()) {
                return RoutingResponse.failure(
                        "No eligible supplier for product: " + item.productCode());
            }
            eligibleByProduct.put(item.productCode(), eligible);
        }

        Map<String, Product> productCache = request.items().stream()
                .collect(Collectors.toMap(
                        OrderItem::productCode,
                        i -> productRepository.findById(i.productCode()).orElseThrow()
                ));
        Map<String, OrderItem> itemsByCode = request.items().stream()
                .collect(Collectors.toMap(OrderItem::productCode, i -> i));

        List<SupplierAssignment> routing = greedyRoute(
                eligibleByProduct, productCache, itemsByCode, request.customerZip());

        return RoutingResponse.success(routing);
    }

    private List<String> validate(OrderRequest request) {
        List<String> errors = new ArrayList<>();
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

        Set<String> remaining = new LinkedHashSet<>(eligibleByProduct.keySet());
        List<SupplierAssignment> assignments = new ArrayList<>();

        while (!remaining.isEmpty()) {
            Map<Supplier, List<String>> coverage = new LinkedHashMap<>();
            for (String productCode : remaining) {
                for (Supplier supplier : eligibleByProduct.get(productCode)) {
                    coverage.computeIfAbsent(supplier, k -> new ArrayList<>()).add(productCode);
                }
            }

            Supplier best = coverage.entrySet().stream()
                    .max(Comparator.comparingInt(e -> e.getValue().size()))
                    .map(Map.Entry::getKey)
                    .orElseThrow();

            String fulfillmentMode = ZipMatcher.matches(best.serviceZips(), customerZip)
                    ? "local" : "mail_order";

            List<RoutedItem> routedItems = coverage.get(best).stream()
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
