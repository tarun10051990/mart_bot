package com.martbot.repository;

import com.martbot.model.Order;
import com.martbot.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findBySessionId(Long sessionId);
    List<Order> findByStatus(OrderStatus status);
}
