import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { SessionsComponent } from './components/sessions/sessions.component';
import { ProductSearchComponent } from './components/product-search/product-search.component';
import { CartComponent } from './components/cart/cart.component';
import { OrdersComponent } from './components/orders/orders.component';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'sessions', component: SessionsComponent },
  { path: 'search', component: ProductSearchComponent },
  { path: 'cart', component: CartComponent },
  { path: 'orders', component: OrdersComponent }
];
