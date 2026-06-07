package com.martbot.repository;

import com.martbot.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
