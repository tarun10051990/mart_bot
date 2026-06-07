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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
     * Strategy: Intercept Algolia/API network responses for reliable data extraction,
     * with DOM-based fallback if API interception fails.
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

            // Intercept API responses containing product data
            AtomicReference<String> apiResponseBody = new AtomicReference<>(null);
            page.onResponse(response -> {
                String url = response.url();
                // Capture Algolia search responses or JioMart's internal product API
                if ((url.contains("algolia") || url.contains("/search") || url.contains("/products") || url.contains("/catalog"))
                        && response.status() == 200
                        && response.headers().getOrDefault("content-type", "").contains("json")) {
                    try {
                        String body = response.text();
                        if (body.contains("\"hits\"") || body.contains("\"products\"") || body.contains("\"items\"") || body.contains("\"title\"")) {
                            apiResponseBody.set(body);
                            log.debug("Captured API response from: {}", url);
                        }
                    } catch (Exception ignored) {}
                }
            });

            String searchUrl = botConfig.getJiomartBaseUrl() + "/search?q=" + query.replace(" ", "+");
            log.info("Navigating to search URL: {}", searchUrl);
            page.navigate(searchUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Give extra time for async API calls to complete
            page.waitForTimeout(3000);

            // Strategy 1: Parse intercepted API response (most reliable)
            String responseBody = apiResponseBody.get();
            if (responseBody != null) {
                results = parseApiResponse(responseBody, maxResults);
                if (!results.isEmpty()) {
                    log.info("Found {} products via API interception for query: {}", results.size(), query);
                    return results;
                }
            }

            // Strategy 2: Use page.evaluate() to extract product data from rendered DOM
            log.info("API interception yielded no results, trying DOM extraction for query: {}", query);
            results = extractProductsFromDOM(page, maxResults);
            if (!results.isEmpty()) {
                log.info("Found {} products via DOM extraction for query: {}", results.size(), query);
                return results;
            }

            // Strategy 3: Log page content for debugging
            String pageTitle = page.title();
            String pageUrl = page.url();
            log.warn("No products found for query '{}'. Page title: '{}', URL: '{}'", query, pageTitle, pageUrl);

            // Check if the page requires pincode/location setup
            String pageContent = page.content();
            if (pageContent.contains("pincode") || pageContent.contains("location") || pageContent.contains("deliver")) {
                log.warn("Page may require pincode/location to be set. Try setting pincode in JioMart first.");
            }

        } catch (Exception e) {
            log.error("Product search failed for query: {}", query, e);
        }

        return results;
    }

    /**
     * Parse product data from intercepted API response (Algolia or JioMart internal API).
     */
    private List<ProductResult> parseApiResponse(String responseBody, int maxResults) {
        List<ProductResult> results = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode root = mapper.readTree(responseBody);

            // Try Algolia format: { "results": [{ "hits": [...] }] }
            JsonNode hits = null;
            if (root.has("results") && root.get("results").isArray() && root.get("results").size() > 0) {
                hits = root.get("results").get(0).get("hits");
            } else if (root.has("hits")) {
                hits = root.get("hits");
            } else if (root.has("products")) {
                hits = root.get("products");
            } else if (root.has("items")) {
                hits = root.get("items");
            } else if (root.has("data") && root.get("data").has("products")) {
                hits = root.get("data").get("products");
            }

            if (hits != null && hits.isArray()) {
                int count = 0;
                for (JsonNode hit : hits) {
                    if (count >= maxResults) break;

                    String name = getJsonText(hit, "title", "name", "product_name", "productName");
                    if (name.isEmpty()) continue;

                    double price = getJsonPrice(hit, "price", "selling_price", "effective_price", "sellingPrice");
                    String url = getJsonText(hit, "url", "product_url", "slug", "link");
                    String imgUrl = getJsonText(hit, "image", "thumbnail", "image_url", "imageUrl", "media");
                    String productId = getJsonText(hit, "id", "product_id", "objectID", "sku", "uid");

                    if (!url.startsWith("http") && !url.isEmpty()) {
                        url = botConfig.getJiomartBaseUrl() + (url.startsWith("/") ? "" : "/") + url;
                    }

                    results.add(new ProductResult(productId, name, url, price, imgUrl, true));
                    count++;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse API response: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Extract products from the rendered DOM using page.evaluate().
     */
    @SuppressWarnings("unchecked")
    private List<ProductResult> extractProductsFromDOM(Page page, int maxResults) {
        List<ProductResult> results = new ArrayList<>();

        try {
            String jsCode = "(maxItems) => {"
                    + "const products = [];"
                    + "const productLinks = document.querySelectorAll('a[href*=\"/p/\"]');"
                    + "const seen = new Set();"
                    + "for (const link of productLinks) {"
                    + "  const href = link.getAttribute('href');"
                    + "  if (seen.has(href)) continue;"
                    + "  seen.add(href);"
                    + "  let card = link.closest('[class*=\"card\"], [class*=\"product\"], [class*=\"item\"], [class*=\"plp\"], li, article') || link.parentElement;"
                    + "  let name = '';"
                    + "  const nameEl = card.querySelector('[class*=\"name\"], [class*=\"title\"], h2, h3, h4, [class*=\"Name\"], [class*=\"Title\"]');"
                    + "  if (nameEl) name = nameEl.textContent.trim();"
                    + "  if (!name) name = link.textContent.trim().split('\\n')[0].trim();"
                    + "  if (!name || name.length > 200) continue;"
                    + "  let price = 0;"
                    + "  const priceEl = card.querySelector('[class*=\"price\"], [class*=\"Price\"], [class*=\"amount\"]');"
                    + "  if (priceEl) {"
                    + "    const priceMatch = priceEl.textContent.match(/[\\d,]+\\.?\\d*/);"
                    + "    if (priceMatch) price = parseFloat(priceMatch[0].replace(/,/g, ''));"
                    + "  }"
                    + "  let img = '';"
                    + "  const imgEl = card.querySelector('img');"
                    + "  if (imgEl) img = imgEl.src || imgEl.getAttribute('data-src') || '';"
                    + "  products.push({ name, price, url: href, img });"
                    + "  if (products.length >= maxItems) break;"
                    + "}"
                    + "if (products.length === 0) {"
                    + "  const allCards = document.querySelectorAll('[class*=\"card\"], [class*=\"product-item\"], [class*=\"listing\"]');"
                    + "  for (const card of allCards) {"
                    + "    const link = card.querySelector('a');"
                    + "    if (!link) continue;"
                    + "    let name = '';"
                    + "    const nameEl = card.querySelector('[class*=\"name\"], [class*=\"title\"], h2, h3, h4');"
                    + "    if (nameEl) name = nameEl.textContent.trim();"
                    + "    if (!name || name.length > 200) continue;"
                    + "    let price = 0;"
                    + "    const priceEl = card.querySelector('[class*=\"price\"]');"
                    + "    if (priceEl) {"
                    + "      const priceMatch = priceEl.textContent.match(/[\\d,]+\\.?\\d*/);"
                    + "      if (priceMatch) price = parseFloat(priceMatch[0].replace(/,/g, ''));"
                    + "    }"
                    + "    let img = '';"
                    + "    const imgEl = card.querySelector('img');"
                    + "    if (imgEl) img = imgEl.src || imgEl.getAttribute('data-src') || '';"
                    + "    products.push({ name, price, url: link.href, img });"
                    + "    if (products.length >= maxItems) break;"
                    + "  }"
                    + "}"
                    + "return products;"
                    + "}";

            List<Map<String, Object>> products = (List<Map<String, Object>>) page.evaluate(jsCode, maxResults);

            if (products != null) {
                for (Map<String, Object> p : products) {
                    String name = String.valueOf(p.getOrDefault("name", ""));
                    if (name.isEmpty()) continue;

                    double price = p.get("price") instanceof Number ? ((Number) p.get("price")).doubleValue() : 0;
                    String url = String.valueOf(p.getOrDefault("url", ""));
                    String imgUrl = String.valueOf(p.getOrDefault("img", ""));
                    String productId = extractProductId(url);

                    results.add(new ProductResult(productId, name, url, price, imgUrl, true));
                }
            }
        } catch (Exception e) {
            log.debug("DOM extraction failed: {}", e.getMessage());
        }

        return results;
    }

    private String getJsonText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText("");
            }
        }
        return "";
    }

    private double getJsonPrice(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.get(field).isNumber()) {
                return node.get(field).asDouble(0);
            }
            // Handle nested price like {"effective": {"min": 100}}
            if (node.has(field) && node.get(field).isObject()) {
                JsonNode priceNode = node.get(field);
                if (priceNode.has("effective")) return priceNode.get("effective").asDouble(0);
                if (priceNode.has("min")) return priceNode.get("min").asDouble(0);
                if (priceNode.has("value")) return priceNode.get("value").asDouble(0);
            }
        }
        return 0;
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
