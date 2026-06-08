export interface BotSession {
  id: number;
  sessionName: string;
  craAccessToken: string;
  craRefreshToken: string;
  ga: string;
  gaXgz: string;
  status: SessionStatus;
  sessionCookies: string;
  lastLoginAt: string;
  createdAt: string;
  updatedAt: string;
}

export type SessionStatus = 'INACTIVE' | 'LOGGING_IN' | 'ACTIVE' | 'EXPIRED' | 'ERROR';

export interface SessionCreateRequest {
  sessionName: string;
  craAccessToken: string;
  craRefreshToken: string;
  ga?: string;
  gaXgz?: string;
}

export interface ProductResult {
  productId: string;
  name: string;
  url: string;
  price: number;
  imageUrl: string;
  available: boolean;
}

export interface CartItem {
  id: number;
  productId: string;
  productName: string;
  productUrl: string;
  price: number;
  quantity: number;
  addedToCart: boolean;
}

export interface Order {
  id: number;
  orderId: string;
  status: OrderStatus;
  deliveryAddress: string;
  pincode: string;
  totalAmount: number;
  paymentMethod: string;
  placedAt: string;
  createdAt: string;
}

export type OrderStatus = 'PENDING' | 'PLACING' | 'PLACED' | 'CONFIRMED' | 'FAILED' | 'CANCELLED';

export interface BotResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
