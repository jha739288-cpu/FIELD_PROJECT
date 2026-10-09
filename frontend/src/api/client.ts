import axios, { AxiosError } from 'axios';
import type { ApiError } from './types';

export const TOKEN_KEY = 'labmarket.token';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  timeout: 10000
});

// Attach the JWT to every request when present (persistent or session storage).
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Expired/invalid token -> drop it from both stores and send the user back to login.
api.interceptors.response.use(
  (res) => res,
  (err: AxiosError<ApiError>) => {
    if (err.response?.status === 401 && !window.location.pathname.startsWith('/login')) {
      localStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      window.location.assign('/login');
    }
    return Promise.reject(err);
  }
);

/** Human-readable message from an Axios failure (prefers the backend ApiError). */
export function apiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiError>(err)) {
    const msg = err.response?.data?.message;
    if (msg) return msg;
    if (err.code === 'ECONNABORTED') return 'Request timed out — is the backend running?';
    if (err.message === 'Network Error') return 'Cannot reach the API — is the backend running?';
  }
  return fallback;
}
