import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { login as apiLogin, me as apiMe, register as apiRegister } from '../api/auth';
import { TOKEN_KEY } from '../api/client';
import type { LoginPayload, RegisterPayload, Role, User } from '../api/types';

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (payload: LoginPayload, rememberMe?: boolean) => Promise<User>;
  register: (payload: RegisterPayload) => Promise<User>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

function homeFor(roles: Role[]): string {
  if (roles.includes('ADMIN')) return '/admin';
  if (roles.includes('VENDOR')) return '/vendor';
  return '/dashboard';
}

export { homeFor };

function storedToken(): string | null {
  return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(storedToken);
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
        clearToken();
        setToken(null);
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, [token]);

  const login = useCallback(async (payload: LoginPayload, rememberMe = true) => {
    const res = await apiLogin(payload);
    // Remember me → persistent storage; otherwise the token dies with the tab.
    (rememberMe ? localStorage : sessionStorage).setItem(TOKEN_KEY, res.token);
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
    clearToken();
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
