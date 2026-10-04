import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          LabMarket
        </Link>
        <nav className="nav">
          {user ? (
            <>
              <Link to="/equipment">Equipment</Link>
              <Link to="/calendar">Calendar</Link>
              <Link to="/bookings">My bookings</Link>
              {(user.roles.includes('LAB_STAFF') || user.roles.includes('ADMIN')) && (
                <Link to="/staff">Staff</Link>
              )}
              {(user.roles.includes('LAB_STAFF') || user.roles.includes('ADMIN')) && (
                <Link to="/analytics">Analytics</Link>
              )}
              {user.roles.includes('ADMIN') && <Link to="/admin">Admin</Link>}
              <span className="nav-user">{user.username}</span>
              <button className="btn btn-ghost" onClick={onLogout}>
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/register" className="btn btn-primary">
                Register
              </Link>
            </>
          )}
        </nav>
      </header>
      <main className="container">{children}</main>
      <footer className="footer">
        LabMarket — usage-based laboratory equipment marketplace · API:{' '}
        {import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'}
      </footer>
    </div>
  );
}
