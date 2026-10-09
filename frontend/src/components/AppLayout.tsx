import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';

interface NavItem {
  to: string;
  label: string;
  icon: string;
  roles?: Role[];
}

const USER_NAV: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: '▦' },
  { to: '/equipment', label: 'Marketplace', icon: '⚗' },
  { to: '/bookings', label: 'My Bookings', icon: '▤' },
  { to: '/predict', label: 'Predictions', icon: '◷' },
  { to: '/profile', label: 'Profile', icon: '☺' }
];

const VENDOR_NAV: NavItem[] = [
  { to: '/vendor', label: 'Dashboard', icon: '▦' },
  { to: '/vendor/equipment', label: 'My Equipment', icon: '⚗' },
  { to: '/vendor/equipment/new', label: 'Add Equipment', icon: '+' },
  { to: '/vendor/bookings', label: 'Bookings', icon: '▤' },
  { to: '/calendar', label: 'Availability', icon: '▦' },
  { to: '/analytics', label: 'Analytics', icon: '◫' },
  { to: '/profile', label: 'Profile', icon: '☺' }
];

const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: '▦' },
  { to: '/admin/users', label: 'Users', icon: '☺' },
  { to: '/admin/vendors', label: 'Vendors', icon: '⚖' },
  { to: '/admin/equipment', label: 'Equipment', icon: '⚗' },
  { to: '/admin/bookings', label: 'Bookings', icon: '▤' },
  { to: '/analytics', label: 'Analytics', icon: '◫' },
  { to: '/admin/settings', label: 'Settings', icon: '⚙' },
  { to: '/profile', label: 'Profile', icon: '☺' }
];

/** Role-based app shell: sidebar on desktop, drawer on mobile. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const onLogout = () => {
    logout();
    navigate('/login');
  };

  const items = !user
    ? USER_NAV
    : user.roles.includes('ADMIN')
      ? ADMIN_NAV
      : user.roles.includes('VENDOR')
        ? VENDOR_NAV
        : USER_NAV;

  return (
    <div className="app">
      <header className="topbar">
        <button
          className="btn btn-ghost drawer-toggle"
          onClick={() => setOpen((o) => !o)}
          aria-label="Toggle navigation"
        >
          ☰
        </button>
        <Link to="/" className="brand">
          LabMarket
        </Link>
        <div className="topbar-right">
          <span className="nav-user">{user?.username}</span>
          <button className="btn btn-ghost" onClick={onLogout}>
            Logout
          </button>
        </div>
      </header>
      <div className="shell">
        <div
          className={`drawer-scrim${open ? ' visible' : ''}`}
          onClick={() => setOpen(false)}
          aria-hidden
        />
        <aside className={`sidebar${open ? ' open' : ''}`}>
          <nav className="side-nav">
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={() => setOpen(false)}
                className={({ isActive }) => (isActive ? 'active' : undefined)}
              >
                <span aria-hidden>{item.icon}</span> {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="sidebar-foot muted small">
            {user?.roles.join(', ')}
          </div>
        </aside>
        <main className="content">{children}</main>
      </div>
      <footer className="footer">
        LabMarket — usage-based laboratory equipment marketplace · API:{' '}
        {import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'}
      </footer>
    </div>
  );
}
