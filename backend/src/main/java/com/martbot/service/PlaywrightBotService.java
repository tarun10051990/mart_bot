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
     * Uses a dedicated page for search to avoid conflicts with cart/order operations.
     * Extracts product data from the rendered DOM using JavaScript evaluation.
     */
    public List<ProductResult> searchProducts(BotSession session, String query, int maxResults) {
        List<ProductResult> results = new ArrayList<>();
        BrowserContext context = activeSessions.get(session.getId());

        if (context == null) {
            log.error("No active session found for session ID: {}", session.getId());
            return results;
        }

        Page searchPage = null;
        try {
            // Use a dedicated page for search to avoid listener conflicts with other operations
            searchPage = context.newPage();

            String searchUrl = botConfig.getJiomartBaseUrl() + "/search?q=" + query.replace(" ", "+");
            log.info("Navigating to search URL: {}", searchUrl);
            searchPage.navigate(searchUrl);
            searchPage.waitForLoadState(LoadState.NETWORKIDLE);

            // Wait for dynamic content to render
            searchPage.waitForTimeout(5000);

            // Extract product data from DOM using JavaScript
            results = extractProductsFromDOM(searchPage, maxResults);
            if (!results.isEmpty()) {
                log.info("Found {} products via DOM extraction for query: {}", results.size(), query);
                return results;
            }

            // If DOM extraction fails, try to get data from page's script tags (SSR data)
            results = extractProductsFromPageData(searchPage, maxResults);
            if (!results.isEmpty()) {
                log.info("Found {} products via page data for query: {}", results.size(), query);
                return results;
            }

            String pageTitle = searchPage.title();
            String currentUrl = searchPage.url();
            log.warn("No products found for query '{}'. Page title: '{}', URL: '{}'", query, pageTitle, currentUrl);

        } catch (Exception e) {
            log.error("Product search failed for query: {}", query, e);
        } finally {
            if (searchPage != null) {
                try { searchPage.close(); } catch (Exception ignored) {}
            }
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
                    + "const seen = new Set();"
                    // Strategy A: Find product links containing /p/ in href
                    + "const productLinks = document.querySelectorAll('a[href*=\"/p/\"], a[href*=\"/product/\"]');"
                    + "for (const link of productLinks) {"
                    + "  const href = link.getAttribute('href');"
                    + "  if (!href || seen.has(href)) continue;"
                    + "  seen.add(href);"
                    + "  let card = link.closest('[class*=\"card\"], [class*=\"product\"], [class*=\"item\"], [class*=\"plp\"], [class*=\"listing\"], li, article, div') || link.parentElement;"
                    // Walk up to find a meaningful container (at least 100px tall or has price info)
                    + "  let attempts = 0;"
                    + "  while (card && card.parentElement && attempts < 5) {"
                    + "    if (card.querySelector('[class*=\"price\"], [class*=\"Price\"], [class*=\"rupee\"], [class*=\"₹\"]')) break;"
                    + "    if (card.offsetHeight > 100) break;"
                    + "    card = card.parentElement;"
                    + "    attempts++;"
                    + "  }"
                    + "  let name = '';"
                    + "  const nameEl = card.querySelector('[class*=\"name\"], [class*=\"title\"], [class*=\"Name\"], [class*=\"Title\"], h2, h3, h4, span[class*=\"line-clamp\"]');"
                    + "  if (nameEl) name = nameEl.textContent.trim();"
                    + "  if (!name) {"
                    + "    const linkText = link.textContent.trim();"
                    + "    if (linkText.length > 5 && linkText.length < 200) name = linkText.split('\\n')[0].trim();"
                    + "  }"
                    + "  if (!name || name.length > 200 || name.length < 3) continue;"
                    // Enhanced price extraction
                    + "  let price = 0;"
                    + "  const priceSelectors = ["
                    + "    '[class*=\"selling\"][class*=\"price\"]',"
                    + "    '[class*=\"offer\"][class*=\"price\"]',"
                    + "    '[class*=\"special\"][class*=\"price\"]',"
                    + "    '[class*=\"price\"] span',"
                    + "    '[class*=\"price\"]',"
                    + "    '[class*=\"Price\"]',"
                    + "    '[class*=\"rupee\"]',"
                    + "    '[class*=\"amount\"]',"
                    + "    'span[class*=\"jm-heading\"]'"
                    + "  ];"
                    + "  for (const sel of priceSelectors) {"
                    + "    const el = card.querySelector(sel);"
                    + "    if (el) {"
                    + "      const text = el.textContent;"
                    + "      const match = text.match(/₹\\s*([\\d,]+\\.?\\d*)|([\\d,]+\\.?\\d*)/);"
                    + "      if (match) {"
                    + "        const val = parseFloat((match[1] || match[2]).replace(/,/g, ''));"
                    + "        if (val > 0 && val < 100000) { price = val; break; }"
                    + "      }"
                    + "    }"
                    + "  }"
                    // If still no price, search all text nodes in the card for ₹ pattern
                    + "  if (price === 0) {"
                    + "    const allText = card.textContent;"
                    + "    const rupeeMatch = allText.match(/₹\\s*([\\d,]+\\.?\\d*)/);"
                    + "    if (rupeeMatch) price = parseFloat(rupeeMatch[1].replace(/,/g, ''));"
                    + "  }"
                    + "  let img = '';"
                    + "  const imgEl = card.querySelector('img[src*=\"http\"], img[data-src*=\"http\"]');"
                    + "  if (imgEl) img = imgEl.src || imgEl.getAttribute('data-src') || '';"
                    + "  products.push({ name, price, url: href, img });"
                    + "  if (products.length >= maxItems) break;"
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

                    if (!url.startsWith("http")) {
                        url = botConfig.getJiomartBaseUrl() + (url.startsWith("/") ? "" : "/") + url;
                    }

                    results.add(new ProductResult(productId, name, url, price, imgUrl, true));
                }
            }
        } catch (Exception e) {
            log.debug("DOM extraction failed: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Extract product data from page script tags (SSR/hydration data).
     */
    @SuppressWarnings("unchecked")
    private List<ProductResult> extractProductsFromPageData(Page page, int maxResults) {
        List<ProductResult> results = new ArrayList<>();

        try {
            String jsCode = "(maxItems) => {"
                    + "const products = [];"
                    // Try __NEXT_DATA__ (Next.js)
                    + "const nextData = document.querySelector('#__NEXT_DATA__');"
                    + "if (nextData) {"
                    + "  try {"
                    + "    const data = JSON.parse(nextData.textContent);"
                    + "    const findProducts = (obj) => {"
                    + "      if (!obj || typeof obj !== 'object') return [];"
                    + "      if (Array.isArray(obj)) return obj.flatMap(findProducts);"
                    + "      if (obj.title && (obj.price || obj.selling_price)) return [obj];"
                    + "      return Object.values(obj).flatMap(findProducts);"
                    + "    };"
                    + "    const items = findProducts(data);"
                    + "    for (const item of items.slice(0, maxItems)) {"
                    + "      products.push({"
                    + "        name: item.title || item.name || '',"
                    + "        price: item.selling_price || item.price || item.effective_price || 0,"
                    + "        url: item.url || item.slug || item.product_url || '',"
                    + "        img: item.image || item.thumbnail || ''"
                    + "      });"
                    + "    }"
                    + "  } catch(e) {}"
                    + "}"
                    // Try window.__PRELOADED_STATE__ or similar
                    + "if (products.length === 0 && window.__PRELOADED_STATE__) {"
                    + "  try {"
                    + "    const state = window.__PRELOADED_STATE__;"
                    + "    const findProducts = (obj) => {"
                    + "      if (!obj || typeof obj !== 'object') return [];"
                    + "      if (Array.isArray(obj)) return obj.flatMap(findProducts);"
                    + "      if (obj.title && (obj.price || obj.selling_price)) return [obj];"
                    + "      return Object.values(obj).flatMap(findProducts);"
                    + "    };"
                    + "    const items = findProducts(state);"
                    + "    for (const item of items.slice(0, maxItems)) {"
                    + "      products.push({"
                    + "        name: item.title || item.name || '',"
                    + "        price: item.selling_price || item.price || 0,"
                    + "        url: item.url || item.slug || '',"
                    + "        img: item.image || item.thumbnail || ''"
                    + "      });"
                    + "    }"
                    + "  } catch(e) {}"
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

                    if (!url.startsWith("http") && !url.isEmpty()) {
                        url = botConfig.getJiomartBaseUrl() + (url.startsWith("/") ? "" : "/") + url;
                    }

                    results.add(new ProductResult(productId, name, url, price, imgUrl, true));
                }
            }
        } catch (Exception e) {
            log.debug("Page data extraction failed: {}", e.getMessage());
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

        Page cartPage = null;
        try {
            // Use a fresh page to avoid stale object issues
            cartPage = context.newPage();

            cartPage.navigate(productUrl);
            cartPage.waitForLoadState(LoadState.NETWORKIDLE);
            cartPage.waitForTimeout(2000);

            // Set quantity if greater than 1
            if (quantity > 1) {
                try {
                    var quantityInput = cartPage.locator("input[type='number'], .quantity-input, [data-testid='quantity-input']").first();
                    if (quantityInput.isVisible()) {
                        quantityInput.fill(String.valueOf(quantity));
                    }
                } catch (Exception e) {
                    log.debug("Quantity input not found, using default quantity");
                }
            }

            // Click Add to Cart button - try multiple selectors
            boolean added = false;
            String[] addToCartSelectors = {
                    "button:has-text('Add to Cart')",
                    "button:has-text('ADD TO CART')",
                    "button:has-text('Add to Basket')",
                    "button:has-text('ADD TO BASKET')",
                    "[data-testid='add-to-cart']",
                    "button[class*='add-to-cart']",
                    "button[class*='addToCart']",
                    "button[class*='add_to_cart']"
            };

            for (String selector : addToCartSelectors) {
                try {
                    var btn = cartPage.locator(selector).first();
                    if (btn.isVisible()) {
                        btn.click(new Locator.ClickOptions().setTimeout(5000));
                        added = true;
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (!added) {
                // Last resort: click any button that looks like "Add" near the price
                try {
                    cartPage.locator("button:has-text('Add')").first()
                            .click(new Locator.ClickOptions().setTimeout(5000));
                    added = true;
                } catch (Exception e) {
                    log.warn("Could not find Add to Cart button on: {}", productUrl);
                }
            }

            if (added) {
                cartPage.waitForTimeout(2000);
                log.info("Product added to cart: {}", productUrl);
            }

            return added;
        } catch (Exception e) {
            log.error("Failed to add product to cart: {}", productUrl, e);
            return false;
        } finally {
            if (cartPage != null) {
                try { cartPage.close(); } catch (Exception ignored) {}
            }
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
     * Fetch saved addresses from JioMart account.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> fetchAddresses(BotSession session) {
        List<Map<String, String>> addresses = new ArrayList<>();
        BrowserContext context = activeSessions.get(session.getId());

        if (context == null) {
            log.error("No active session found for session ID: {}", session.getId());
            return addresses;
        }

        Page addressPage = null;
        try {
            addressPage = context.newPage();

            // Navigate to the address management page
            addressPage.navigate(botConfig.getJiomartBaseUrl() + "/profile/addresses");
            addressPage.waitForLoadState(LoadState.NETWORKIDLE);
            addressPage.waitForTimeout(3000);

            // Extract addresses from the page using JavaScript
            String jsCode = "() => {"
                    + "const addresses = [];"
                    + "const addressCards = document.querySelectorAll('[class*=\"address\"], [class*=\"Address\"], [data-testid*=\"address\"]');"
                    + "for (const card of addressCards) {"
                    + "  const text = card.textContent.trim();"
                    + "  if (text.length < 10) continue;"
                    // Skip cards that are just buttons like "Add New Address"
                    + "  if (text.toLowerCase().includes('add new') && text.length < 30) continue;"
                    + "  let name = '';"
                    + "  const nameEl = card.querySelector('[class*=\"name\"], [class*=\"Name\"], strong, b');"
                    + "  if (nameEl) name = nameEl.textContent.trim();"
                    + "  let phone = '';"
                    + "  const phoneMatch = text.match(/(\\+91|91)?\\s*[6-9]\\d{9}/);"
                    + "  if (phoneMatch) phone = phoneMatch[0].trim();"
                    + "  let pincode = '';"
                    + "  const pincodeMatch = text.match(/\\b[1-9]\\d{5}\\b/);"
                    + "  if (pincodeMatch) pincode = pincodeMatch[0];"
                    + "  let type = 'Home';"
                    + "  if (text.toLowerCase().includes('office') || text.toLowerCase().includes('work')) type = 'Office';"
                    + "  let fullAddress = text.replace(name, '').replace(phone, '').trim();"
                    + "  fullAddress = fullAddress.replace(/\\s+/g, ' ').trim();"
                    + "  if (fullAddress.length < 5) continue;"
                    + "  addresses.push({ name, phone, pincode, type, fullAddress });"
                    + "}"
                    // Fallback: if no address cards found, try to get from visible text blocks
                    + "if (addresses.length === 0) {"
                    + "  const allDivs = document.querySelectorAll('div, li, section');"
                    + "  for (const div of allDivs) {"
                    + "    const text = div.textContent.trim();"
                    + "    const pincodeMatch = text.match(/\\b[1-9]\\d{5}\\b/);"
                    + "    if (pincodeMatch && text.length > 20 && text.length < 500) {"
                    + "      const existing = addresses.find(a => a.fullAddress === text);"
                    + "      if (!existing) {"
                    + "        addresses.push({ name: '', phone: '', pincode: pincodeMatch[0], type: 'Home', fullAddress: text });"
                    + "      }"
                    + "    }"
                    + "    if (addresses.length >= 10) break;"
                    + "  }"
                    + "}"
                    + "return addresses;"
                    + "}";

            List<Map<String, String>> result = (List<Map<String, String>>) addressPage.evaluate(jsCode);
            if (result != null) {
                addresses.addAll(result);
            }

            log.info("Found {} addresses for session: {}", addresses.size(), session.getId());
        } catch (Exception e) {
            log.error("Failed to fetch addresses for session: {}", session.getId(), e);
        } finally {
            if (addressPage != null) {
                try { addressPage.close(); } catch (Exception ignored) {}
            }
        }

        return addresses;
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
