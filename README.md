# MartBot - JioMart Automation Bot

A full-stack automation bot for JioMart built with **Java + Playwright** (backend) and **Angular** (frontend).

## Features

- **Automated Login**: Navigate to Reliance Retail login URL, inject cookies (cra_access_token, cra_refresh_token, _ga, _ga_XGZ513W4JV), refresh and click Continue
- **Session Management**: Create, refresh, and monitor multiple bot sessions
- **Product Search**: Search JioMart products via browser automation
- **Cart Automation**: Add products to cart programmatically
- **COD Order Placement**: Automated Cash on Delivery order workflow
- **REST API**: Spring Boot API wrapping all bot functionality
- **Angular Dashboard**: Full-featured admin UI

## Architecture

```
mart_bot/
├── backend/          # Spring Boot + Playwright (Java 17)
│   ├── src/main/java/com/martbot/
│   │   ├── config/       # App configuration
│   │   ├── controller/   # REST API endpoints
│   │   ├── dto/          # Request/Response DTOs
│   │   ├── model/        # JPA entities
│   │   ├── repository/   # Spring Data repositories
│   │   └── service/      # Business logic + Playwright bot
│   └── src/main/resources/
│       └── application.yml
└── frontend/         # Angular 18 (standalone components)
    └── src/app/
        ├── components/   # Dashboard, Sessions, Search, Cart, Orders
        ├── models/       # TypeScript interfaces
        └── services/     # HTTP API service
```

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 18+
- npm 9+

## Quick Start

### Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The backend runs on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
ng serve
```

The frontend runs on `http://localhost:4200`.

## API Endpoints

### Sessions
- `POST /api/sessions` - Create session with cookies and auto-login
- `GET /api/sessions` - List all sessions
- `GET /api/sessions/{id}` - Get session details
- `GET /api/sessions/active` - List active sessions
- `POST /api/sessions/{id}/refresh` - Refresh/re-login session
- `GET /api/sessions/{id}/health` - Check session health
- `DELETE /api/sessions/{id}` - Delete session

### Cart
- `POST /api/cart/search` - Search products
- `POST /api/cart/add` - Add product to cart
- `GET /api/cart/items/{sessionId}` - Get cart items
- `DELETE /api/cart/clear/{sessionId}` - Clear cart

### Orders
- `POST /api/orders/cod` - Place COD order
- `GET /api/orders/session/{sessionId}` - Orders by session
- `GET /api/orders` - All orders
- `GET /api/orders/status/{status}` - Orders by status

## How It Works

1. **User provides cookies**: Extract `cra_access_token`, `cra_refresh_token`, `_ga`, and `_ga_XGZ513W4JV` from browser
2. **Bot navigates to login URL**: Opens Reliance Retail auth page
3. **Cookies injected**: Bot sets cookies in browser context
4. **Page refreshed**: Reload triggers session recognition
5. **Continue clicked**: Bot clicks the Continue button to complete auth
6. **Session active**: Bot can now browse, search, add to cart, and place orders

## Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
bot:
  login-url: "https://account.relianceretail.com/login/..."
  jiomart-base-url: "https://www.jiomart.com"
  headless: true        # Set to false for debugging
  timeout-ms: 30000     # Browser action timeout
```
