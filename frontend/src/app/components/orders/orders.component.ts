import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { BotSession, Order } from '../../models/session.model';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.scss']
})
export class OrdersComponent implements OnInit {
  sessions: BotSession[] = [];
  selectedSessionId: number | null = null;
  orders: Order[] = [];
  showOrderForm = false;
  loading = false;
  loadingAddresses = false;
  message = '';
  savedAddresses: any[] = [];

  orderForm = {
    deliveryAddress: '',
    pincode: ''
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getActiveSessions().subscribe(res => {
      if (res.success) {
        this.sessions = res.data;
      }
    });
    this.loadAllOrders();
  }

  fetchAddresses(): void {
    if (!this.selectedSessionId) return;
    this.loadingAddresses = true;
    this.api.getAddresses(this.selectedSessionId).subscribe({
      next: res => {
        this.loadingAddresses = false;
        if (res.success) {
          this.savedAddresses = res.data;
        }
      },
      error: () => {
        this.loadingAddresses = false;
        this.message = 'Failed to fetch addresses';
      }
    });
  }

  useAddress(address: any): void {
    this.orderForm.deliveryAddress = address.fullAddress || '';
    this.orderForm.pincode = address.pincode || '';
  }

  loadAllOrders(): void {
    this.api.getAllOrders().subscribe(res => {
      if (res.success) {
        this.orders = res.data;
      }
    });
  }

  loadSessionOrders(): void {
    if (!this.selectedSessionId) {
      this.loadAllOrders();
      return;
    }
    this.api.getOrdersBySession(this.selectedSessionId).subscribe(res => {
      if (res.success) {
        this.orders = res.data;
      }
    });
  }

  placeOrder(): void {
    if (!this.selectedSessionId) return;

    this.loading = true;
    this.message = '';
    this.api.placeOrderCOD(
      this.selectedSessionId,
      this.orderForm.deliveryAddress,
      this.orderForm.pincode
    ).subscribe({
      next: res => {
        this.loading = false;
        if (res.success && res.data.status === 'PLACED') {
          this.message = `Order placed! ID: ${res.data.orderId}`;
          this.showOrderForm = false;
          this.loadSessionOrders();
        } else {
          this.message = 'Order placement failed: ' + res.message;
        }
      },
      error: err => {
        this.loading = false;
        this.message = 'Order error: ' + err.message;
      }
    });
  }
}
