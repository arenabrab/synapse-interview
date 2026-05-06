package com.synapseinterview.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OrderRequest(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("customer_zip") String customerZip,
        @JsonProperty("mail_order") boolean mailOrder,
        List<OrderItem> items,
        String priority,
        String notes
) {}
