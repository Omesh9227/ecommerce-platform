import { NavLink, Outlet, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Logo } from '../components/Logo';

const NAV: [string, string][] = [
  ['/admin', 'Dashboard'],
  ['/admin/orders', 'Orders'],
  ['/admin/shipments', 'Shipments'],
  ['/admin/products', 'Products'],
  ['/admin/categories', 'Categories'],
  ['/admin/inventory', 'Inventory'],
  ['/admin/reviews', 'Reviews'],
  ['/admin/customers', 'Customers'],
];

export function AdminLayout() {
  const { user, logout } = useAuth();
  return (
    <div className="flex min-h-screen flex-col bg-mist">
      <header className="bg-ink text-white">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <div className="flex items-center gap-3">
            <Logo />
            <span className="rounded bg-marigold px-2 py-0.5 text-xs font-bold text-ink">Admin</span>
          </div>
          <div className="ml-auto flex items-center gap-5 text-sm">
            <Link to="/" className="hover:underline">
              View shop
            </Link>
            <span className="hidden text-white/70 sm:inline">{user?.email}</span>
            <button type="button" onClick={logout} className="hover:underline">
              Sign out
            </button>
          </div>
        </div>
      </header>
      <div className="flex flex-1 flex-col md:flex-row">
        <nav aria-label="Admin" className="border-b border-line bg-white md:w-52 md:shrink-0 md:border-b-0 md:border-r">
          <ul className="flex gap-1 overflow-x-auto p-2 md:flex-col md:p-3">
            {NAV.map(([to, label]) => (
              <li key={to} className="shrink-0">
                <NavLink
                  to={to}
                  end={to === '/admin'}
                  className={({ isActive }) =>
                    `block rounded-md px-3 py-2 text-sm font-semibold ${isActive ? 'bg-ink text-white' : 'text-ink hover:bg-mist'}`
                  }
                >
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <main id="main" className="min-w-0 flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
