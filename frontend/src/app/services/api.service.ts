import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BotSession,
  SessionCreateRequest,
  ProductResult,
  CartItem,
  Order,
  BotResponse,
  OrderStatus
} from '../models/session.model';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // Session endpoints
  createSession(request: SessionCreateRequest): Observable<BotResponse<BotSession>> {
    return this.http.post<BotResponse<BotSession>>(`${this.baseUrl}/sessions`, request);
  }

  getAllSessions(): Observable<BotResponse<BotSession[]>> {
    return this.http.get<BotResponse<BotSession[]>>(`${this.baseUrl}/sessions`);
  }

  getSession(id: number): Observable<BotResponse<BotSession>> {
    return this.http.get<BotResponse<BotSession>>(`${this.baseUrl}/sessions/${id}`);
  }

  getActiveSessions(): Observable<BotResponse<BotSession[]>> {
    return this.http.get<BotResponse<BotSession[]>>(`${this.baseUrl}/sessions/active`);
  }

  refreshSession(id: number): Observable<BotResponse<BotSession>> {
    return this.http.post<BotResponse<BotSession>>(`${this.baseUrl}/sessions/${id}/refresh`, {});
  }

  checkSessionHealth(id: number): Observable<BotResponse<boolean>> {
    return this.http.get<BotResponse<boolean>>(`${this.baseUrl}/sessions/${id}/health`);
  }

  deleteSession(id: number): Observable<BotResponse<void>> {
    return this.http.delete<BotResponse<void>>(`${this.baseUrl}/sessions/${id}`);
  }

  // Cart endpoints
  searchProducts(sessionId: number, query: string, maxResults: number = 10): Observable<BotResponse<ProductResult[]>> {
    return this.http.post<BotResponse<ProductResult[]>>(`${this.baseUrl}/cart/search`, {
      sessionId, query, maxResults
    });
  }

  addToCart(sessionId: number, productUrl: string, quantity: number = 1): Observable<BotResponse<CartItem>> {
    return this.http.post<BotResponse<CartItem>>(`${this.baseUrl}/cart/add`, {
      sessionId, productUrl, quantity
    });
  }

  getCartItems(sessionId: number): Observable<BotResponse<CartItem[]>> {
    return this.http.get<BotResponse<CartItem[]>>(`${this.baseUrl}/cart/items/${sessionId}`);
  }

  clearCart(sessionId: number): Observable<BotResponse<void>> {
    return this.http.delete<BotResponse<void>>(`${this.baseUrl}/cart/clear/${sessionId}`);
  }

  // Order endpoints
  placeOrderCOD(sessionId: number, deliveryAddress: string, pincode: string): Observable<BotResponse<Order>> {
    return this.http.post<BotResponse<Order>>(`${this.baseUrl}/orders/cod`, {
      sessionId, deliveryAddress, pincode
    });
  }

  getOrdersBySession(sessionId: number): Observable<BotResponse<Order[]>> {
    return this.http.get<BotResponse<Order[]>>(`${this.baseUrl}/orders/session/${sessionId}`);
  }

  getAllOrders(): Observable<BotResponse<Order[]>> {
    return this.http.get<BotResponse<Order[]>>(`${this.baseUrl}/orders`);
  }

  getOrdersByStatus(status: OrderStatus): Observable<BotResponse<Order[]>> {
    return this.http.get<BotResponse<Order[]>>(`${this.baseUrl}/orders/status/${status}`);
  }
}
