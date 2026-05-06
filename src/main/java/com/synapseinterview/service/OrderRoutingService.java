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