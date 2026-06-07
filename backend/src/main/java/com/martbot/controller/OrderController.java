package com.martbot.controller;

import com.martbot.dto.BotResponse;
import com.martbot.dto.PlaceOrderRequest;
import com.martbot.model.Order;
import com.martbot.model.OrderStatus;
import com.martbot.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/cod")
    public ResponseEntity<BotResponse<Order>> placeOrderCOD(@Valid @RequestBody PlaceOrderRequest request) {
        try {
            Order order = orderService.placeOrderCOD(
                    request.getSessionId(), request.getDeliveryAddress(), request.getPincode());
            return ResponseEntity.ok(BotResponse.success(
                    order.getStatus() == OrderStatus.PLACED ? "Order placed successfully" : "Order placement failed",
                    order));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BotResponse.error("Order failed: " + e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<BotResponse<List<Order>>> getOrdersBySession(@PathVariable Long sessionId) {
        List<Order> orders = orderService.getOrdersBySession(sessionId);
        return ResponseEntity.ok(BotResponse.success("Orders retrieved", orders));
    }

    @GetMapping
    public ResponseEntity<BotResponse<List<Order>>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(BotResponse.success("All orders retrieved", orders));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<BotResponse<List<Order>>> getOrdersByStatus(@PathVariable OrderStatus status) {
        List<Order> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(BotResponse.success("Orders by status retrieved", orders));
    }
}
