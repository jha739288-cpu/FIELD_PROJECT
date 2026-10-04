import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';

interface Props {
  children: JSX.Element;
  roles?: Role[];
}

/** Requires authentication; with `roles`, also requires one of them (else 403 page). */
export default function ProtectedRoute({ children, roles }: Props) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <p className="muted">Loading…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !roles.some((r) => user.roles.includes(r))) {
    return <Navigate to="/forbidden" replace />;
  }
  return children;
}
