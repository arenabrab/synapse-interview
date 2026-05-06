package com.synapseinterview.service;

import com.synapseinterview.model.*;
import com.synapseinterview.repository.ProductRepository;
import com.synapseinterview.repository.SupplierRepository;
import com.synapseinterview.util.ZipMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRoutingServiceTest {

    @Mock ProductRepository productRepository;
    @Mock SupplierRepository supplierRepository;
    @InjectMocks OrderRoutingService service;

    private Product widgetProduct;
    private Product gadgetProduct;
    private Supplier localSupplier;     // serves 10000-10099, both categories, no mail order
    private Supplier mailOnlySupplier;  // serves 99999 only, widgets only, can mail order

    @BeforeEach
    void setUp() {
        widgetProduct = new Product("P-001", "Widget", "widgets");
        gadgetProduct = new Product("P-002", "Gadget", "gadgets");

        localSupplier = new Supplier(
                "SUP-LOCAL", "Local Co",
                ZipMatcher.parse("10000-10099"),
                Set.of("widgets", "gadgets"),
                8, false
        );
        mailOnlySupplier = new Supplier(
                "SUP-MAIL", "Mail Co",
                ZipMatcher.parse("99999"),
                Set.of("widgets"),
                9, true
        );
    }

    @Test
    void validationFailsOnEmptyItems() {
        OrderRequest request = new OrderRequest("ORD-X", "10050", false, List.of(), "standard", null);
        RoutingResponse response = service.route(request);
        assertFalse(response.feasible());
        assertTrue(response.errors().contains("Order must include at least one line item."));
    }

    @Test
    void validationFailsOnNullItems() {
        OrderRequest request = new OrderRequest("ORD-X", "10050", false, null, "standard", null);
        RoutingResponse response = service.route(request);
        assertFalse(response.feasible());
        assertTrue(response.errors().contains("Order must include at least one line item."));
    }

    @Test
    void validationFailsOnInvalidZip() {
        OrderRequest request = new OrderRequest("ORD-X", "ABCDE", false,
                List.of(new OrderItem("P-001", 1)), "standard", null);
        RoutingResponse response = service.route(request);
        assertFalse(response.feasible());
        assertTrue(response.errors().contains("Order must include a valid customer_zip."));
    }

    @Test
    void validationCollectsBothErrors() {
        OrderRequest request = new OrderRequest("ORD-X", null, false, null, "standard", null);
        RoutingResponse response = service.route(request);
        assertFalse(response.feasible());
        assertEquals(2, response.errors().size());
    }

    @Test
    void unknownProductReturnsFeasibleFalse() {
        when(productRepository.findById("UNKNOWN")).thenReturn(Optional.empty());
        OrderRequest request = new OrderRequest("ORD-X", "10050", false,
                List.of(new OrderItem("UNKNOWN", 1)), "standard", null);
        RoutingResponse response = service.route(request);
        assertFalse(response.feasible());
        assertTrue(response.errors().get(0).contains("UNKNOWN"));
    }

    @Test
    void noEligibleSupplierReturnsFeasibleFalse() {
        when(productRepository.findById("P-001")).thenReturn(Optional.of(widgetProduct));
        when(supplierRepository.findEligible("widgets", "55555", false)).thenReturn(List.of());
        OrderRequest request = new OrderRequest("ORD-X", "55555", false,
                List.of(new OrderItem("P-001", 1)), "standard", null);
        RoutingResponse response = service.route(request);
        assertFalse(response.feasible());
        assertTrue(response.errors().get(0).contains("P-001"));
    }

    @Test
    void localRoutingSuccess() {
        when(productRepository.findById("P-001")).thenReturn(Optional.of(widgetProduct));
        when(supplierRepository.findEligible("widgets", "10050", false))
                .thenReturn(List.of(localSupplier));
        OrderRequest request = new OrderRequest("ORD-X", "10050", false,
                List.of(new OrderItem("P-001", 1)), "standard", null);
        RoutingResponse response = service.route(request);
        assertTrue(response.feasible());
        assertEquals(1, response.routing().size());
        assertEquals("SUP-LOCAL", response.routing().get(0).supplierId());
        assertEquals("local", response.routing().get(0).items().get(0).fulfillmentMode());
    }

    @Test
    void mailOrderRoutingSuccess() {
        when(productRepository.findById("P-001")).thenReturn(Optional.of(widgetProduct));
        when(supplierRepository.findEligible("widgets", "55555", true))
                .thenReturn(List.of(mailOnlySupplier));
        OrderRequest request = new OrderRequest("ORD-X", "55555", true,
                List.of(new OrderItem("P-001", 1)), "standard", null);
        RoutingResponse response = service.route(request);
        assertTrue(response.feasible());
        assertEquals("mail_order", response.routing().get(0).items().get(0).fulfillmentMode());
    }

    @Test
    void consolidationUsesFewestSuppliers() {
        // Both products can be served by localSupplier → should produce 1 assignment
        when(productRepository.findById("P-001")).thenReturn(Optional.of(widgetProduct));
        when(productRepository.findById("P-002")).thenReturn(Optional.of(gadgetProduct));
        when(supplierRepository.findEligible("widgets", "10050", false))
                .thenReturn(List.of(localSupplier));
        when(supplierRepository.findEligible("gadgets", "10050", false))
                .thenReturn(List.of(localSupplier));
        OrderRequest request = new OrderRequest("ORD-X", "10050", false,
                List.of(new OrderItem("P-001", 1), new OrderItem("P-002", 1)), "standard", null);
        RoutingResponse response = service.route(request);
        assertTrue(response.feasible());
        assertEquals(1, response.routing().size());
        assertEquals(2, response.routing().get(0).items().size());
    }

    @Test
    void routedItemContainsAllFields() {
        when(productRepository.findById("P-001")).thenReturn(Optional.of(widgetProduct));
        when(supplierRepository.findEligible("widgets", "10050", false))
                .thenReturn(List.of(localSupplier));
        OrderRequest request = new OrderRequest("ORD-X", "10050", false,
                List.of(new OrderItem("P-001", 3)), "standard", null);
        RoutingResponse response = service.route(request);
        RoutedItem item = response.routing().get(0).items().get(0);
        assertEquals("P-001", item.productCode());
        assertEquals(3, item.quantity());
        assertEquals("widgets", item.category());
        assertEquals("local", item.fulfillmentMode());
    }
}
