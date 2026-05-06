package com.synapseinterview.controller;

import tools.jackson.databind.ObjectMapper;
import com.synapseinterview.model.*;
import com.synapseinterview.service.OrderRoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean OrderRoutingService routingService;

    @Test
    void routeReturnsFeasibleResponse() throws Exception {
        RoutingResponse mockResponse = RoutingResponse.success(List.of(
                new SupplierAssignment("SUP-001", "Test Supplier",
                        List.of(new RoutedItem("WC-STD-001", 1, "wheelchair", "local")))
        ));
        when(routingService.route(any())).thenReturn(mockResponse);

        OrderRequest request = new OrderRequest(
                "ORD-TEST", "10050", false,
                List.of(new OrderItem("WC-STD-001", 1)),
                "standard", null
        );

        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.routing[0].supplier_id").value("SUP-001"))
                .andExpect(jsonPath("$.routing[0].supplier_name").value("Test Supplier"))
                .andExpect(jsonPath("$.routing[0].items[0].product_code").value("WC-STD-001"))
                .andExpect(jsonPath("$.routing[0].items[0].quantity").value(1))
                .andExpect(jsonPath("$.routing[0].items[0].category").value("wheelchair"))
                .andExpect(jsonPath("$.routing[0].items[0].fulfillment_mode").value("local"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void routeReturnsInfeasibleResponseWithErrors() throws Exception {
        RoutingResponse mockResponse = RoutingResponse.failure(List.of(
                "Order must include at least one line item.",
                "Order must include a valid customer_zip."
        ));
        when(routingService.route(any())).thenReturn(mockResponse);

        OrderRequest request = new OrderRequest(
                "ORD-TEST", "10050", false,
                List.of(new OrderItem("P-001", 1)),
                "standard", null
        );

        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Order must include at least one line item."))
                .andExpect(jsonPath("$.errors[1]").value("Order must include a valid customer_zip."))
                .andExpect(jsonPath("$.routing").doesNotExist());
    }
}
