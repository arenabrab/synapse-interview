package com.synapseinterview.model;

import java.util.List;

public record RoutingResponse(
        boolean feasible,
        List<SupplierAssignment> routing,
        List<String> errors
) {
    public static RoutingResponse success(List<SupplierAssignment> routing) {
        return new RoutingResponse(true, routing, null);
    }

    public static RoutingResponse failure(List<String> errors) {
        return new RoutingResponse(false, null, errors);
    }

    public static RoutingResponse failure(String error) {
        return new RoutingResponse(false, null, List.of(error));
    }
}
