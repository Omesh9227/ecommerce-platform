import { Route, Routes } from 'react-router-dom';
import { RequireAdmin } from '../auth/RequireAdmin';
import { AdminLayout } from './AdminLayout';
import { CategoriesPage } from './pages/CategoriesPage';
import { CustomersPage } from './pages/CustomersPage';
import { DashboardPage } from './pages/DashboardPage';
import { InventoryPage } from './pages/InventoryPage';
import { OrderPage } from './pages/OrderPage';
import { OrdersPage } from './pages/OrdersPage';
import { ProductFormPage } from './pages/ProductFormPage';
import { ProductsPage } from './pages/ProductsPage';
import { ReviewsPage } from './pages/ReviewsPage';
import { ShipmentPage, ShipmentsPage } from './pages/ShipmentsPage';

/** Everything under /admin. Loaded only when an admin page is opened, so shoppers never download it. */
export default function AdminApp() {
  return (
    <RequireAdmin>
      <Routes>
        <Route element={<AdminLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:orderNumber" element={<OrderPage />} />
          <Route path="shipments" element={<ShipmentsPage />} />
          <Route path="shipments/:id" element={<ShipmentPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="products/:id" element={<ProductFormPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="inventory" element={<InventoryPage />} />
          <Route path="reviews" element={<ReviewsPage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="*" element={<p className="text-ink-soft">There’s no admin page here.</p>} />
        </Route>
      </Routes>
    </RequireAdmin>
  );
}
