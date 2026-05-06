package com.synapseinterview.controller;

import com.synapseinterview.model.OrderRequest;
import com.synapseinterview.model.RoutingResponse;
import com.synapseinterview.service.OrderRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRoutingService routingService;

    @PostMapping("/route")
    public ResponseEntity<RoutingResponse> route(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(routingService.route(request));
    }
}