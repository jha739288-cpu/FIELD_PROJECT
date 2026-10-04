import { api } from './client';
import type { PagedResponse, UsageRecord } from './types';

export async function myUsage(size = 100): Promise<PagedResponse<UsageRecord>> {
  const { data } = await api.get<PagedResponse<UsageRecord>>('/usage/my', {
    params: { size }
  });
  return data;
}
