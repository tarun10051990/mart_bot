import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { BotSession, CartItem } from '../../models/session.model';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss']
})
export class CartComponent implements OnInit {
  sessions: BotSession[] = [];
  selectedSessionId: number | null = null;
  cartItems: CartItem[] = [];
  message = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getActiveSessions().subscribe(res => {
      if (res.success) {
        this.sessions = res.data;
      }
    });
  }

  loadCart(): void {
    if (!this.selectedSessionId) return;

    this.api.getCartItems(this.selectedSessionId).subscribe(res => {
      if (res.success) {
        this.cartItems = res.data;
      }
    });
  }

  clearCart(): void {
    if (!this.selectedSessionId) return;
    if (!confirm('Clear all cart items?')) return;

    this.api.clearCart(this.selectedSessionId).subscribe(res => {
      if (res.success) {
        this.cartItems = [];
        this.message = 'Cart cleared';
      }
    });
  }
}
