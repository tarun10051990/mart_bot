package com.martbot.service;

import com.martbot.model.BotSession;
import com.martbot.model.Order;
import com.martbot.model.OrderStatus;
import com.martbot.model.PaymentMethod;
import com.martbot.repository.BotSessionRepository;
import com.martbot.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final PlaywrightBotService botService;
    private final BotSessionRepository sessionRepository;
    private final OrderRepository orderRepository;

    public OrderService(PlaywrightBotService botService, BotSessionRepository sessionRepository,
                        OrderRepository orderRepository) {
        this.botService = botService;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order placeOrderCOD(Long sessionId, String address, String pincode) {
        BotSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        Order order = new Order();
        order.setSession(session);
        order.setDeliveryAddress(address);
        order.setPincode(pincode);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.PLACING);
        order = orderRepository.save(order);

        Map<String, Object> result = botService.placeOrderCOD(session, address, pincode);

        if (Boolean.TRUE.equals(result.get("success"))) {
            order.setStatus(OrderStatus.PLACED);
            order.setOrderId((String) result.get("orderId"));
            order.setPlacedAt(LocalDateTime.now());
            log.info("COD order placed successfully: {}", order.getOrderId());
        } else {
            order.setStatus(OrderStatus.FAILED);
            log.error("COD order placement failed: {}", result.get("error"));
        }

        return orderRepository.save(order);
    }

    public List<Order> getOrdersBySession(Long sessionId) {
        return orderRepository.findBySessionId(sessionId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
}
