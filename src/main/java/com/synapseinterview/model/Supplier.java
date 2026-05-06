package com.synapseinterview.model;

import java.util.List;
import java.util.Set;

public record Supplier(
        String supplierId,
        String supplierName,
        List<ZipRange> serviceZips,
        Set<String> productCategories,
        Integer satisfactionScore,
        boolean canMailOrder
) {}
