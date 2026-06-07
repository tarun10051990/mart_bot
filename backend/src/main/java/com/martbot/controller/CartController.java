package com.martbot.controller;

import com.martbot.dto.AddToCartRequest;
import com.martbot.dto.BotResponse;
import com.martbot.dto.ProductResult;
import com.martbot.dto.ProductSearchRequest;
import com.martbot.model.CartItem;
import com.martbot.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/search")
    public ResponseEntity<BotResponse<List<ProductResult>>> searchProducts(
            @Valid @RequestBody ProductSearchRequest request) {
        try {
            List<ProductResult> results = cartService.searchProducts(
                    request.getSessionId(), request.getQuery(), request.getMaxResults());
            return ResponseEntity.ok(BotResponse.success("Search completed", results));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BotResponse.error("Search failed: " + e.getMessage()));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<BotResponse<CartItem>> addToCart(@Valid @RequestBody AddToCartRequest request) {
        try {
            CartItem item = cartService.addToCart(
                    request.getSessionId(), request.getProductUrl(), request.getQuantity());
            return ResponseEntity.ok(BotResponse.success(
                    item.isAddedToCart() ? "Product added to cart" : "Failed to add product to cart", item));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BotResponse.error("Add to cart failed: " + e.getMessage()));
        }
    }

    @GetMapping("/items/{sessionId}")
    public ResponseEntity<BotResponse<List<CartItem>>> getCartItems(@PathVariable Long sessionId) {
        List<CartItem> items = cartService.getCartItems(sessionId);
        return ResponseEntity.ok(BotResponse.success("Cart items retrieved", items));
    }

    @DeleteMapping("/clear/{sessionId}")
    public ResponseEntity<BotResponse<Void>> clearCart(@PathVariable Long sessionId) {
        cartService.clearCart(sessionId);
        return ResponseEntity.ok(BotResponse.success("Cart cleared"));
    }
}
