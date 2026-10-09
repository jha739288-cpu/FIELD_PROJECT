import { api } from './client';
import type { PagedResponse, Role, UserAdmin, VendorSummary } from './types';

export interface UserQuery {
  q?: string;
  role?: Role | '';
  enabled?: boolean;
  page?: number;
  size?: number;
}

export async function listUsers(query: UserQuery = {}): Promise<PagedResponse<UserAdmin>> {
  const params: Record<string, string | number | boolean> = {
    page: query.page ?? 0,
    size: query.size ?? 15
  };
  if (query.q) params.q = query.q;
  if (query.role) params.role = query.role;
  if (query.enabled !== undefined) params.enabled = query.enabled;
  const { data } = await api.get<PagedResponse<UserAdmin>>('/users', { params });
  return data;
}

export async function getUser(id: number | string): Promise<UserAdmin> {
  const { data } = await api.get<UserAdmin>(`/users/${id}`);
  return data;
}

export async function setUserEnabled(id: number | string, enabled: boolean): Promise<UserAdmin> {
  const { data } = await api.put<UserAdmin>(`/users/${id}/status`, { enabled });
  return data;
}

export async function setUserRoles(id: number | string, roles: Role[]): Promise<UserAdmin> {
  const { data } = await api.put<UserAdmin>(`/users/${id}/roles`, { roles });
  return data;
}

export async function listVendors(q?: string, page = 0, size = 15): Promise<PagedResponse<VendorSummary>> {
  const { data } = await api.get<PagedResponse<VendorSummary>>('/users/vendors', {
    params: q ? { q, page, size } : { page, size }
  });
  return data;
}
