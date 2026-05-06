package com.synapseinterview.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SupplierAssignment(
        @JsonProperty("supplier_id") String supplierId,
        @JsonProperty("supplier_name") String supplierName,
        List<RoutedItem> items
) {}
