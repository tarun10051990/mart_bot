import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { BotSession, Order } from '../../models/session.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  sessions: BotSession[] = [];
  orders: Order[] = [];
  activeSessions = 0;
  totalOrders = 0;
  successfulOrders = 0;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.api.getAllSessions().subscribe(res => {
      if (res.success) {
        this.sessions = res.data;
        this.activeSessions = this.sessions.filter(s => s.status === 'ACTIVE').length;
      }
    });

    this.api.getAllOrders().subscribe(res => {
      if (res.success) {
        this.orders = res.data;
        this.totalOrders = this.orders.length;
        this.successfulOrders = this.orders.filter(o => o.status === 'PLACED' || o.status === 'CONFIRMED').length;
      }
    });
  }
}
