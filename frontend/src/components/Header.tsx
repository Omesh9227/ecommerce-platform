import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { auth as authApi, cart as cartApi, catalog } from '../api/shop';
import type { CategoryNode } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Logo } from './Logo';
import { SearchBox } from './SearchBox';

export function Header() {
  const { user, logout } = useAuth();
  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get, enabled: !!user, meta: { private: true } });
  const me = useQuery({ queryKey: ['me'], queryFn: authApi.me, enabled: !!user, meta: { private: true }, staleTime: 5 * 60_000 });
  const count = user ? cart.data?.totalQuantity ?? 0 : 0;

  return (
    <header className="bg-ink text-white">
      <div className="mx-auto flex max-w-page flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
        <Logo />
        <div className="order-last w-full md:order-none md:w-auto md:flex-1">
          <SearchBox />
        </div>
        <nav aria-label="Your account" className="ml-auto flex items-center gap-5 text-sm">
          {user ? (
            <AccountMenu label={me.data ? `Hi, ${me.data.firstName}` : user.email} onSignOut={logout} />
          ) : (
            <Link to="/login" className="font-semibold hover:underline">
              Sign in
            </Link>
          )}
          <NavLink to="/orders" className="hidden hover:underline sm:block">
            Orders
          </NavLink>
          <NavLink to="/wishlist" className="hidden hover:underline sm:block">
            Wishlist
          </NavLink>
          <Link to="/cart" className="flex items-center gap-2 font-semibold hover:underline" aria-label={`Cart, ${count} items`}>
            <CartIcon />
            <span
              data-testid="cart-count"
              className="min-w-[1.5rem] rounded-full bg-marigold px-1.5 text-center font-display text-sm font-extrabold text-ink"
            >
              {count}
            </span>
          </Link>
        </nav>
      </div>
      <CategoryBar />
    </header>
  );
}

function AccountMenu({ label, onSignOut }: { label: string; onSignOut: () => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const location = useLocation();
  useEffect(() => setOpen(false), [location]);
  useEffect(() => {
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);
  return (
    <div ref={ref} className="relative">
      <button type="button" aria-expanded={open} onClick={() => setOpen((o) => !o)} className="max-w-[12rem] truncate hover:underline">
        {label}
      </button>
      {open && (
        <div className="absolute right-0 z-30 mt-2 w-48 rounded-md border border-line bg-white py-1 text-ink shadow-lg">
          <MenuLink to="/account">Your account</MenuLink>
          <MenuLink to="/orders">Your orders</MenuLink>
          <MenuLink to="/wishlist">Wishlist</MenuLink>
          <button type="button" onClick={onSignOut} className="block w-full px-4 py-2 text-left text-sm hover:bg-mist">
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

function MenuLink({ to, children }: { to: string; children: string }) {
  return (
    <Link to={to} className="block px-4 py-2 text-sm hover:bg-mist">
      {children}
    </Link>
  );
}

function CategoryBar() {
  const categories = useQuery({ queryKey: ['categories'], queryFn: catalog.categories, staleTime: 5 * 60_000 });
  const top: CategoryNode[] = categories.data ?? [];
  if (top.length === 0) return null;
  return (
    <nav aria-label="Categories" className="bg-ink-soft/40">
      <ul className="mx-auto flex max-w-page gap-5 overflow-x-auto px-4 py-2 text-sm">
        {top.map((c) => (
          <li key={c.id} className="shrink-0">
            <Link to={`/search?categoryId=${c.id}`} className="hover:underline">
              {c.name}
            </Link>
          </li>
        ))}
        <li className="shrink-0">
          <Link to="/track" className="hover:underline">
            Track a parcel
          </Link>
        </li>
      </ul>
    </nav>
  );
}

function CartIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path d="M3 4h2l2.2 10.2a1.5 1.5 0 0 0 1.5 1.2h8.6a1.5 1.5 0 0 0 1.5-1.1L21 8H6.2" />
      <circle cx="9.5" cy="19.5" r="1.4" />
      <circle cx="17" cy="19.5" r="1.4" />
    </svg>
  );
}
