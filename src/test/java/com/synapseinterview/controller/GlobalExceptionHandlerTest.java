package com.synapseinterview.controller;

import com.synapseinterview.service.OrderRoutingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderRoutingService routingService;

    @Test
    void missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Request body is missing or malformed."));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Request body is missing or malformed."));
    }

    @Test
    void wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/orders/route")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Content-Type must be application/json."));
    }
}
