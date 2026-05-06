package com.synapseinterview.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoutedItem(
        @JsonProperty("product_code") String productCode,
        int quantity,
        String category,
        @JsonProperty("fulfillment_mode") String fulfillmentMode
) {}
