import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { login as apiLogin, me as apiMe, register as apiRegister } from '../api/auth';
import { TOKEN_KEY } from '../api/client';
import type { LoginPayload, RegisterPayload, Role, User } from '../api/types';

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<User>;
  register: (payload: RegisterPayload) => Promise<User>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

function homeFor(roles: Role[]): string {
  if (roles.includes('ADMIN')) return '/admin';
  if (roles.includes('LAB_STAFF')) return '/staff';
  return '/dashboard';
}

export { homeFor };

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    apiMe()
      .then(setUser)
      .catch(() => {
        localStorage.removeItem(TOKEN_KEY);
        setToken(null);
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, [token]);

  const login = useCallback(async (payload: LoginPayload) => {
    const res = await apiLogin(payload);
    localStorage.setItem(TOKEN_KEY, res.token);
    setToken(res.token);
    const profile = await apiMe();
    setUser(profile);
    return profile;
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    const res = await apiRegister(payload);
    localStorage.setItem(TOKEN_KEY, res.token);
    setToken(res.token);
    const profile = await apiMe();
    setUser(profile);
    return profile;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
    setUser(null);
  }, []);

  const hasRole = useCallback(
    (...roles: Role[]) => user !== null && roles.some((r) => user.roles.includes(r)),
    [user]
  );

  const value = useMemo(
    () => ({ user, token, loading, login, register, logout, hasRole }),
    [user, token, loading, login, register, logout, hasRole]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
