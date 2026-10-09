import { api } from './client';
import type {
  Equipment,
  EquipmentCreatePayload,
  EquipmentStatus,
  EquipmentUpdatePayload,
  PagedResponse
} from './types';

export interface EquipmentQuery {
  q?: string;
  status?: EquipmentStatus | '';
  category?: string;
  laboratory?: string;
  mine?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listEquipment(query: EquipmentQuery): Promise<PagedResponse<Equipment>> {
  const params: Record<string, string | number | boolean> = { page: query.page ?? 0, size: query.size ?? 12 };
  if (query.q) params.q = query.q;
  if (query.status) params.status = query.status;
  if (query.category) params.category = query.category;
  if (query.laboratory) params.laboratory = query.laboratory;
  if (query.mine) params.mine = true;
  if (query.sort) params.sort = query.sort;
  const { data } = await api.get<PagedResponse<Equipment>>('/equipment', { params });
  return data;
}

export async function getEquipment(id: number | string): Promise<Equipment> {
  const { data } = await api.get<Equipment>(`/equipment/${id}`);
  return data;
}

export async function createEquipment(payload: EquipmentCreatePayload): Promise<Equipment> {
  const { data } = await api.post<Equipment>('/equipment', payload);
  return data;
}

export async function updateEquipment(
  id: number | string,
  payload: EquipmentUpdatePayload
): Promise<Equipment> {
  const { data } = await api.put<Equipment>(`/equipment/${id}`, payload);
  return data;
}

export async function deleteEquipment(id: number | string): Promise<void> {
  await api.delete(`/equipment/${id}`);
}
