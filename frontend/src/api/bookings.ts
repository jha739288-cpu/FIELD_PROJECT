import { api } from './client';
import type {
  Availability,
  Booking,
  BookingCreatePayload,
  BookingStatus,
  CalendarResponse,
  EquipmentSchedule,
  PagedResponse
} from './types';

export interface BookingQuery {
  equipmentId?: number;
  status?: BookingStatus | '';
  page?: number;
  size?: number;
}

function params(query: BookingQuery): Record<string, string | number> {
  const p: Record<string, string | number> = {
    page: query.page ?? 0,
    size: query.size ?? 20
  };
  if (query.equipmentId !== undefined) p.equipmentId = query.equipmentId;
  if (query.status) p.status = query.status;
  return p;
}

export async function listBookings(query: BookingQuery): Promise<PagedResponse<Booking>> {
  const { data } = await api.get<PagedResponse<Booking>>('/bookings', { params: params(query) });
  return data;
}

export async function myBookings(
  status?: BookingStatus | '',
  page = 0,
  size = 20
): Promise<PagedResponse<Booking>> {
  const { data } = await api.get<PagedResponse<Booking>>('/bookings/my', {
    params: status ? { status, page, size } : { page, size }
  });
  return data;
}

export async function getBooking(id: number | string): Promise<Booking> {
  const { data } = await api.get<Booking>(`/bookings/${id}`);
  return data;
}

export async function createBooking(payload: BookingCreatePayload): Promise<Booking> {
  const { data } = await api.post<Booking>('/bookings', payload);
  return data;
}

export async function cancelBooking(id: number | string): Promise<Booking> {
  const { data } = await api.put<Booking>(`/bookings/${id}/cancel`);
  return data;
}

export async function confirmBooking(id: number | string): Promise<Booking> {
  const { data } = await api.put<Booking>(`/bookings/${id}/confirm`);
  return data;
}

export async function rejectBooking(id: number | string): Promise<Booking> {
  const { data } = await api.put<Booking>(`/bookings/${id}/reject`);
  return data;
}

export async function equipmentSchedule(
  equipmentId: number | string,
  from: string,
  to: string
): Promise<EquipmentSchedule> {
  const { data } = await api.get<EquipmentSchedule>(`/bookings/equipment/${equipmentId}`, {
    params: { from, to }
  });
  return data;
}

export async function equipmentAvailability(
  equipmentId: number | string,
  from: string,
  to: string
): Promise<Availability> {
  const { data } = await api.get<Availability>(`/bookings/equipment/${equipmentId}/availability`, {
    params: { from, to }
  });
  return data;
}

export async function bookingCalendar(
  from: string,
  to: string,
  equipmentId?: number
): Promise<CalendarResponse> {
  const { data } = await api.get<CalendarResponse>('/bookings/calendar', {
    params: equipmentId ? { from, to, equipmentId } : { from, to }
  });
  return data;
}
