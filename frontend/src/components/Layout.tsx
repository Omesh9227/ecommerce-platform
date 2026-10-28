import { Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import { Header } from './Header';

export function Layout() {
  const { pathname } = useLocation();
  useEffect(() => window.scrollTo(0, 0), [pathname]);
  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <Header />
      <main id="main" className="mx-auto w-full max-w-page flex-1 px-4 py-6">
        <Outlet />
      </main>
      <footer className="border-t border-line bg-white">
        <div className="mx-auto flex max-w-page flex-wrap gap-x-8 gap-y-2 px-4 py-6 text-sm text-ink-soft">
          <span>Cartwheel, a practice shop. Payments are simulated: no real money moves.</span>
        </div>
      </footer>
    </div>
  );
}
