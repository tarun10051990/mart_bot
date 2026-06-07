package com.martbot.service;

import com.martbot.dto.ProductResult;
import com.martbot.model.BotSession;
import com.martbot.model.CartItem;
import com.martbot.repository.BotSessionRepository;
import com.martbot.repository.CartItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final PlaywrightBotService botService;
    private final BotSessionRepository sessionRepository;
    private final CartItemRepository cartItemRepository;

    public CartService(PlaywrightBotService botService, BotSessionRepository sessionRepository,
                       CartItemRepository cartItemRepository) {
        this.botService = botService;
        this.sessionRepository = sessionRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public List<ProductResult> searchProducts(Long sessionId, String query, int maxResults) {
        BotSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        return botService.searchProducts(session, query, maxResults);
    }

    public ProductResult fetchProductByUrl(Long sessionId, String productUrl) {
        BotSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        return botService.fetchProductByUrl(session, productUrl);
    }

    @Transactional
    public CartItem addToCart(Long sessionId, String productUrl, int quantity) {
        BotSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        boolean success = botService.addToCart(session, productUrl, quantity);

        CartItem item = new CartItem();
        item.setSession(session);
        item.setProductUrl(productUrl);
        item.setQuantity(quantity);
        item.setProductId(extractProductId(productUrl));
        item.setAddedToCart(success);

        if (success) {
            log.info("Product added to cart for session {}: {}", sessionId, productUrl);
        } else {
            log.error("Failed to add product to cart for session {}: {}", sessionId, productUrl);
        }

        return cartItemRepository.save(item);
    }

    public List<CartItem> getCartItems(Long sessionId) {
        return cartItemRepository.findBySessionId(sessionId);
    }

    @Transactional
    public void clearCart(Long sessionId) {
        cartItemRepository.deleteBySessionId(sessionId);
        log.info("Cart cleared for session: {}", sessionId);
    }

    private String extractProductId(String url) {
        if (url == null) return "unknown";
        String[] parts = url.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "unknown";
    }
}
