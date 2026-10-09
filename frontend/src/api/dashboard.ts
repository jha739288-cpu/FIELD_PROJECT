import { api } from './client';
import type {
  AdminOverview,
  BookingAnalytics,
  EquipmentDashboard,
  MySummary,
  SensorAnalytics,
  Summary,
  UsageTrend,
  Utilization,
  VendorDashboard
} from './types';

export interface WindowParams {
  from?: string;
  to?: string;
}

export async function getSummary(): Promise<Summary> {
  const { data } = await api.get<Summary>('/dashboard/summary');
  return data;
}

export async function getUtilization(
  from: string,
  to: string,
  equipmentId?: number
): Promise<Utilization> {
  const { data } = await api.get<Utilization>('/dashboard/utilization', {
    params: equipmentId ? { from, to, equipmentId } : { from, to }
  });
  return data;
}

export async function getEquipmentDashboard(
  id: number | string,
  window?: WindowParams
): Promise<EquipmentDashboard> {
  const { data } = await api.get<EquipmentDashboard>(`/dashboard/equipment/${id}`, {
    params: window ?? {}
  });
  return data;
}

export async function getUsageTrend(window?: WindowParams): Promise<UsageTrend> {
  const { data } = await api.get<UsageTrend>('/dashboard/usage', { params: window ?? {} });
  return data;
}

export async function getConflictCount(window?: WindowParams): Promise<number> {
  const { data } = await api.get<{ conflictAttempts: number }>('/dashboard/conflicts', {
    params: window ?? {}
  });
  return data.conflictAttempts;
}

export async function getBookingAnalytics(window?: WindowParams): Promise<BookingAnalytics> {
  const { data } = await api.get<BookingAnalytics>('/dashboard/bookings', {
    params: window ?? {}
  });
  return data;
}

export async function getSensorAnalytics(window?: WindowParams): Promise<SensorAnalytics> {
  const { data } = await api.get<SensorAnalytics>('/dashboard/sensors', {
    params: window ?? {}
  });
  return data;
}

export async function getMySummary(): Promise<MySummary> {
  const { data } = await api.get<MySummary>('/dashboard/my-summary');
  return data;
}

export async function getAdminOverview(): Promise<AdminOverview> {
  const { data } = await api.get<AdminOverview>('/dashboard/admin/overview');
  return data;
}

export async function getVendorDashboard(): Promise<VendorDashboard> {
  const { data } = await api.get<VendorDashboard>('/dashboard/vendor');
  return data;
}

/** Last `days` as an ISO window ending now (matches backend trailing windows). */
export function trailingDays(days: number): WindowParams {
  const to = new Date();
  const from = new Date(to.getTime() - days * 86_400_000);
  return { from: from.toISOString(), to: to.toISOString() };
}
