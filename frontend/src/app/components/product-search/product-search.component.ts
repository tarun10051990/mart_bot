import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { BotSession, ProductResult } from '../../models/session.model';

@Component({
  selector: 'app-product-search',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './product-search.component.html',
  styleUrls: ['./product-search.component.scss']
})
export class ProductSearchComponent implements OnInit {
  sessions: BotSession[] = [];
  selectedSessionId: number | null = null;
  searchQuery = '';
  products: ProductResult[] = [];
  loading = false;
  message = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getActiveSessions().subscribe(res => {
      if (res.success) {
        this.sessions = res.data;
      }
    });
  }

  search(): void {
    if (!this.selectedSessionId || !this.searchQuery.trim()) return;

    this.loading = true;
    this.message = '';
    this.api.searchProducts(this.selectedSessionId, this.searchQuery).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.products = res.data;
          this.message = `Found ${this.products.length} products`;
        } else {
          this.message = 'Search failed: ' + res.message;
        }
      },
      error: err => {
        this.loading = false;
        this.message = 'Search error: ' + err.message;
      }
    });
  }

  addToCart(product: ProductResult): void {
    if (!this.selectedSessionId) return;

    this.api.addToCart(this.selectedSessionId, product.url).subscribe(res => {
      if (res.success && res.data.addedToCart) {
        this.message = `"${product.name}" added to cart!`;
      } else {
        this.message = 'Failed to add to cart';
      }
    });
  }
}
