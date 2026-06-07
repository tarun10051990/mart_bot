package com.martbot.service;

import com.martbot.config.BotConfig;
import com.martbot.dto.ProductResult;
import com.martbot.model.BotSession;
import com.martbot.model.SessionStatus;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlaywrightBotService {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBotService.class);

    private final BotConfig botConfig;
    private Playwright playwright;
    private Browser browser;
    private final Map<Long, BrowserContext> activeSessions = new ConcurrentHashMap<>();

    public PlaywrightBotService(BotConfig botConfig) {
        this.botConfig = botConfig;
        initBrowser();
    }

    private void initBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(botConfig.isHeadless())
                    .setArgs(Arrays.asList(
                            "--disable-blink-features=AutomationControlled",
                            "--no-sandbox",
                            "--disable-dev-shm-usage"
                    )));
            log.info("Playwright browser initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Playwright browser", e);
        }
    }

    /**
     * Step 1: Navigate to the Reliance Retail login URL and click the login link.
     * Step 2: Inject user-provided cookies (cra_access_token, cra_refresh_token, _ga, _ga_XGZ513W4JV).
     * Step 3: Refresh the page and click the "Continue" button.
     */
    public Map<String, Object> loginWithCookies(BotSession session) {
        Map<String, Object> result = new HashMap<>();

        try {
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080));

            // Apply stealth settings
            context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            Page page = context.newPage();
            page.setDefaultTimeout(botConfig.getTimeoutMs());

            // Step 1: Navigate to login URL
            log.info("Navigating to login URL for session: {}", session.getSessionName());
            page.navigate(botConfig.getLoginUrl());
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Step 2: Inject cookies
            log.info("Injecting cookies for session: {}", session.getSessionName());
            List<Cookie> cookies = buildCookies(session);
            context.addCookies(cookies);

            // Step 3: Refresh and click Continue
            log.info("Refreshing page and clicking Continue button");
            page.reload();
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Wait for and click the Continue button
            try {
                page.locator("button:has-text('Continue'), a:has-text('Continue'), input[value='Continue']")
                        .first()
                        .click(new Locator.ClickOptions().setTimeout(10000));
                log.info("Clicked Continue button successfully");
            } catch (Exception e) {
                log.warn("Continue button not found, attempting alternative selectors");
                try {
                    page.locator("[data-testid='continue-btn'], .continue-btn, #continue-button")
                            .first()
                            .click(new Locator.ClickOptions().setTimeout(5000));
                } catch (Exception ex) {
                    log.warn("Alternative continue button also not found: {}", ex.getMessage());
                }
            }

            // Wait for post-login page to load
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Harvest all cookies after login
            List<Cookie> allCookies = context.cookies();
            StringBuilder cookieStr = new StringBuilder();
            for (Cookie cookie : allCookies) {
                if (!cookieStr.isEmpty()) cookieStr.append("; ");
                cookieStr.append(cookie.name).append("=").append(cookie.value);
            }

            // Store the context for reuse
            activeSessions.put(session.getId(), context);

            result.put("success", true);
            result.put("cookies", cookieStr.toString());
            result.put("url", page.url());
            log.info("Login successful for session: {}, URL: {}", session.getSessionName(), page.url());

        } catch (Exception e) {
            log.error("Login failed for session: {}", session.getSessionName(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Search for products on JioMart.
     */
    public List<ProductResult> searchProducts(BotSession session, String query, int maxResults) {
        List<ProductResult> results = new ArrayList<>();
        BrowserContext context = activeSessions.get(session.getId());

        if (context == null) {
            log.error("No active session found for session ID: {}", session.getId());
            return results;
        }

        try {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            String searchUrl = botConfig.getJiomartBaseUrl() + "/search/" + query.replace(" ", "%20");
            page.navigate(searchUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Wait for product listings to load
            page.locator(".plp-card-container, .product-card, [data-testid='product-card']")
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(10000));

            // Extract product information
            var productCards = page.locator(".plp-card-container, .product-card, [data-testid='product-card']").all();

            int count = 0;
            for (var card : productCards) {
                if (count >= maxResults) break;

                try {
                    String name = card.locator(".plp-card-details__name, .product-name, h3").textContent();
                    String priceText = card.locator(".plp-card-details__price, .product-price, .jm-heading-xxs").textContent();
                    String url = card.locator("a").first().getAttribute("href");
                    String imgUrl = card.locator("img").first().getAttribute("src");

                    double price = extractPrice(priceText);
                    String productId = extractProductId(url);

                    ProductResult product = new ProductResult(
                            productId, name, url != null ? botConfig.getJiomartBaseUrl() + url : "",
                            price, imgUrl != null ? imgUrl : "", true
                    );
                    results.add(product);
                    count++;
                } catch (Exception e) {
                    log.debug("Failed to parse product card: {}", e.getMessage());
                }
            }

            log.info("Found {} products for query: {}", results.size(), query);
        } catch (Exception e) {
            log.error("Product search failed for query: {}", query, e);
        }

        return results;
    }

    /**
     * Add a product to the cart.
     */
    public boolean addToCart(BotSession session, String productUrl, int quantity) {
        BrowserContext context = activeSessions.get(session.getId());

        if (context == null) {
            log.error("No active session found for session ID: {}", session.getId());
            return false;
        }

        try {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            page.navigate(productUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Set quantity if greater than 1
            if (quantity > 1) {
                var quantityInput = page.locator("input[type='number'], .quantity-input, [data-testid='quantity-input']").first();
                if (quantityInput.isVisible()) {
                    quantityInput.fill(String.valueOf(quantity));
                }
            }

            // Click Add to Cart button
            page.locator("button:has-text('Add to Cart'), button:has-text('ADD TO CART'), [data-testid='add-to-cart']")
                    .first()
                    .click(new Locator.ClickOptions().setTimeout(10000));

            // Wait for cart update confirmation
            page.waitForTimeout(2000);

            log.info("Product added to cart: {}", productUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to add product to cart: {}", productUrl, e);
            return false;
        }
    }

    /**
     * Place order with COD payment method.
     */
    public Map<String, Object> placeOrderCOD(BotSession session, String address, String pincode) {
        Map<String, Object> result = new HashMap<>();
        BrowserContext context = activeSessions.get(session.getId());

        if (context == null) {
            result.put("success", false);
            result.put("error", "No active session found");
            return result;
        }

        try {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            // Navigate to cart
            page.navigate(botConfig.getJiomartBaseUrl() + "/checkout/cart");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Click Proceed to Checkout / Place Order
            page.locator("button:has-text('Place Order'), button:has-text('Proceed'), button:has-text('Checkout'), [data-testid='checkout-btn']")
                    .first()
                    .click(new Locator.ClickOptions().setTimeout(10000));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Handle address - check if we need to enter a new address
            try {
                var pincodeInput = page.locator("input[placeholder*='Pincode'], input[name='pincode'], [data-testid='pincode-input']").first();
                if (pincodeInput.isVisible()) {
                    pincodeInput.fill(pincode);
                    // Click check/verify pincode
                    page.locator("button:has-text('Check'), button:has-text('Verify')").first()
                            .click(new Locator.ClickOptions().setTimeout(5000));
                    page.waitForTimeout(2000);
                }

                var addressInput = page.locator("textarea[name='address'], input[name='address'], [data-testid='address-input']").first();
                if (addressInput.isVisible()) {
                    addressInput.fill(address);
                }

                // Save address if needed
                page.locator("button:has-text('Save'), button:has-text('Deliver Here')").first()
                        .click(new Locator.ClickOptions().setTimeout(5000));
                page.waitForLoadState(LoadState.NETWORKIDLE);
            } catch (Exception e) {
                log.info("Using existing address or address step skipped: {}", e.getMessage());
            }

            // Select COD payment method
            try {
                page.locator("input[value='COD'], label:has-text('Cash on Delivery'), [data-testid='cod-option']")
                        .first()
                        .click(new Locator.ClickOptions().setTimeout(10000));
                page.waitForTimeout(1000);
            } catch (Exception e) {
                log.warn("COD selection failed, trying alternatives: {}", e.getMessage());
                page.locator("text=Cash on Delivery, text=Pay on Delivery").first()
                        .click(new Locator.ClickOptions().setTimeout(5000));
            }

            // Confirm order
            page.locator("button:has-text('Place Order'), button:has-text('Confirm Order'), [data-testid='place-order-btn']")
                    .first()
                    .click(new Locator.ClickOptions().setTimeout(10000));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Extract order ID from confirmation page
            String pageText = page.textContent("body");
            String orderId = extractOrderId(pageText);

            result.put("success", true);
            result.put("orderId", orderId);
            result.put("url", page.url());
            log.info("Order placed successfully: {}", orderId);

        } catch (Exception e) {
            log.error("Order placement failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Get current session status by checking if we can access authenticated pages.
     */
    public boolean isSessionActive(Long sessionId) {
        BrowserContext context = activeSessions.get(sessionId);
        if (context == null) return false;

        try {
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            page.navigate(botConfig.getJiomartBaseUrl() + "/profile");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            return !page.url().contains("login");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Close a specific session.
     */
    public void closeSession(Long sessionId) {
        BrowserContext context = activeSessions.remove(sessionId);
        if (context != null) {
            try {
                context.close();
                log.info("Session closed: {}", sessionId);
            } catch (Exception e) {
                log.warn("Error closing session: {}", sessionId, e);
            }
        }
    }

    private List<Cookie> buildCookies(BotSession session) {
        List<Cookie> cookies = new ArrayList<>();

        cookies.add(new Cookie("cra_access_token", session.getCraAccessToken())
                .setDomain(".relianceretail.com")
                .setPath("/"));

        cookies.add(new Cookie("cra_refresh_token", session.getCraRefreshToken())
                .setDomain(".relianceretail.com")
                .setPath("/"));

        if (session.getGa() != null && !session.getGa().isEmpty()) {
            cookies.add(new Cookie("_ga", session.getGa())
                    .setDomain(".jiomart.com")
                    .setPath("/"));
        }

        if (session.getGaXgz() != null && !session.getGaXgz().isEmpty()) {
            cookies.add(new Cookie("_ga_XGZ513W4JV", session.getGaXgz())
                    .setDomain(".jiomart.com")
                    .setPath("/"));
        }

        return cookies;
    }

    private double extractPrice(String priceText) {
        if (priceText == null) return 0;
        String cleaned = priceText.replaceAll("[^\\d.]", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractProductId(String url) {
        if (url == null) return UUID.randomUUID().toString();
        String[] parts = url.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : UUID.randomUUID().toString();
    }

    private String extractOrderId(String pageText) {
        if (pageText == null) return "UNKNOWN";
        // Try to find order ID pattern in the text
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:Order|order)\\s*(?:ID|Id|id|#)?\\s*:?\\s*([A-Z0-9-]+)");
        java.util.regex.Matcher matcher = pattern.matcher(pageText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "ORD-" + System.currentTimeMillis();
    }

    @PreDestroy
    public void cleanup() {
        activeSessions.values().forEach(ctx -> {
            try {
                ctx.close();
            } catch (Exception ignored) {}
        });
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("Playwright resources cleaned up");
    }
}
