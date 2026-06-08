import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { ApiService } from './services/api.service';

interface CartProduct {
  name: string;
  url: string;
  price: number;
  imageUrl: string;
  quantity: number;
  available: boolean;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  // Session state
  sessions: any[] = [];
  selectedSessionId: number | null = null;
  sessionForm = {
    sessionName: '',
    craAccessToken: '',
    craRefreshToken: '',
    ga: '',
    gaXgz: ''
  };
  creatingSession = false;
  sessionMessage = '';

  // Products state
  productUrl = '';
  fetchingProduct = false;
  productMessage = '';
  cart: CartProduct[] = [];

  // Address state
  addresses: any[] = [];
  fetchingAddresses = false;
  selectedAddress: any = null;
  addressMessage = '';

  // Order state
  placingOrder = false;
  orderMessage = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  // --- Session Methods ---
  loadSessions(): void {
    this.api.getAllSessions().subscribe(res => {
      if (res.success) {
        this.sessions = res.data;
      }
    });
  }

  createSession(): void {
    if (!this.sessionForm.sessionName || !this.sessionForm.craAccessToken) return;
    this.creatingSession = true;
    this.sessionMessage = 'Creating session & logging in...';
    this.api.createSession(this.sessionForm).subscribe({
      next: res => {
        this.creatingSession = false;
        if (res.success) {
          this.sessionMessage = 'Session created! Status: ' + res.data.status;
          this.selectedSessionId = res.data.id;
          this.loadSessions();
        } else {
          this.sessionMessage = 'Failed: ' + res.message;
        }
      },
      error: err => {
        this.creatingSession = false;
        this.sessionMessage = 'Error: ' + (err.error?.message || err.message);
      }
    });
  }

  selectSession(id: number): void {
    this.selectedSessionId = id;
    this.addresses = [];
    this.selectedAddress = null;
  }

  // --- Product Methods ---
  fetchProduct(): void {
    if (!this.selectedSessionId || !this.productUrl) return;
    this.fetchingProduct = true;
    this.productMessage = 'Fetching product details...';
    this.api.fetchProductByUrl(this.selectedSessionId, this.productUrl.trim()).subscribe({
      next: res => {
        this.fetchingProduct = false;
        if (res.success && res.data) {
          this.productMessage = '';
          const existing = this.cart.find(p => p.url === res.data.url);
          if (existing) {
            existing.quantity++;
            this.productMessage = 'Product already in cart, quantity increased.';
          } else {
            this.cart.push({
              name: res.data.name,
              url: res.data.url,
              price: res.data.price,
              imageUrl: res.data.imageUrl,
              quantity: 1,
              available: res.data.available
            });
          }
          this.productUrl = '';
        } else {
          this.productMessage = 'Could not fetch product. Check the URL and try again.';
        }
      },
      error: err => {
        this.fetchingProduct = false;
        this.productMessage = 'Error: ' + (err.error?.message || err.message);
      }
    });
  }

  removeFromCart(index: number): void {
    this.cart.splice(index, 1);
  }

  getCartTotal(): number {
    return this.cart.reduce((sum, p) => sum + (p.price * p.quantity), 0);
  }

  // --- Address Methods ---
  fetchAddresses(): void {
    if (!this.selectedSessionId) return;
    this.fetchingAddresses = true;
    this.addressMessage = 'Fetching addresses from JioMart...';
    this.api.getAddresses(this.selectedSessionId).subscribe({
      next: res => {
        this.fetchingAddresses = false;
        if (res.success && res.data.length > 0) {
          this.addresses = res.data;
          this.addressMessage = '';
        } else {
          this.addressMessage = 'No addresses found. Make sure you have saved addresses in JioMart.';
        }
      },
      error: () => {
        this.fetchingAddresses = false;
        this.addressMessage = 'Failed to fetch addresses.';
      }
    });
  }

  selectAddress(addr: any): void {
    this.selectedAddress = addr;
  }

  // --- Order Methods ---
  placeOrder(): void {
    if (!this.selectedSessionId || this.cart.length === 0 || !this.selectedAddress) return;
    this.placingOrder = true;
    this.orderMessage = 'Clearing existing JioMart cart...';

    // Step 1: Clear existing JioMart cart first
    this.api.clearJioMartCart(this.selectedSessionId!).subscribe({
      next: () => {
        this.addProductsToCart();
      },
      error: () => {
        // Even if clear fails, proceed with adding
        this.addProductsToCart();
      }
    });
  }

  private addProductsToCart(): void {
    this.orderMessage = 'Adding products to JioMart cart...';
    let addedCount = 0;
    const totalProducts = this.cart.length;

    for (const product of this.cart) {
      this.api.addToCart(this.selectedSessionId!, product.url, product.quantity).subscribe({
        next: () => {
          addedCount++;
          this.orderMessage = `Added ${addedCount}/${totalProducts} products to cart...`;
          if (addedCount === totalProducts) {
            this.placeCODOrder();
          }
        },
        error: () => {
          addedCount++;
          if (addedCount === totalProducts) {
            this.placeCODOrder();
          }
        }
      });
    }
  }

  private placeCODOrder(): void {
    this.orderMessage = 'Placing COD order...';
    this.api.placeOrderCOD(
      this.selectedSessionId!,
      this.selectedAddress.fullAddress || '',
      this.selectedAddress.pincode || ''
    ).subscribe({
      next: res => {
        this.placingOrder = false;
        if (res.success) {
          this.orderMessage = 'Order placed successfully! Order ID: ' + (res.data.orderId || 'Pending');
          this.cart = [];
        } else {
          this.orderMessage = 'Order failed: ' + res.message;
        }
      },
      error: err => {
        this.placingOrder = false;
        this.orderMessage = 'Order error: ' + (err.error?.message || err.message);
      }
    });
  }
}
