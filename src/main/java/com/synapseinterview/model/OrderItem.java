package com.synapseinterview.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItem(
        @JsonProperty("product_code") String productCode,
        int quantity
) {}
