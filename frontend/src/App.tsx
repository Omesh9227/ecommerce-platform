import { Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { AccountPage } from './pages/AccountPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { OrderPage } from './pages/OrderPage';
import { OrdersPage } from './pages/OrdersPage';
import { PayPage } from './pages/PayPage';
import { ProductPage } from './pages/ProductPage';
import { RegisterPage } from './pages/RegisterPage';
import { SearchPage } from './pages/SearchPage';
import { TrackPage } from './pages/TrackPage';
import { WishlistPage } from './pages/WishlistPage';

const signedIn = (page: JSX.Element) => <RequireAuth>{page}</RequireAuth>;

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="products/:id" element={<ProductPage />} />
        <Route path="track" element={<TrackPage />} />
        <Route path="track/:trackingNumber" element={<TrackPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="cart" element={signedIn(<CartPage />)} />
        <Route path="checkout" element={signedIn(<CheckoutPage />)} />
        <Route path="orders" element={signedIn(<OrdersPage />)} />
        <Route path="orders/:orderNumber" element={signedIn(<OrderPage />)} />
        <Route path="orders/:orderNumber/pay" element={signedIn(<PayPage />)} />
        <Route path="wishlist" element={signedIn(<WishlistPage />)} />
        <Route path="account" element={signedIn(<AccountPage />)} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
