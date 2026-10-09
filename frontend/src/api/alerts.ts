import { api } from './client';
import type { Alert, AlertStatus, PagedResponse } from './types';

export async function listAlerts(
  status?: AlertStatus | '',
  page = 0,
  size = 20
): Promise<PagedResponse<Alert>> {
  const { data } = await api.get<PagedResponse<Alert>>('/alerts', {
    params: status ? { status, page, size } : { page, size }
  });
  return data;
}

export async function openOverdueAlerts(page = 0, size = 20): Promise<PagedResponse<Alert>> {
  const { data } = await api.get<PagedResponse<Alert>>('/alerts/overdue', {
    params: { page, size }
  });
  return data;
}

export async function resolveAlert(id: number | string, note?: string): Promise<Alert> {
  const { data } = await api.put<Alert>(`/alerts/${id}/resolve`, { note: note ?? null });
  return data;
}
