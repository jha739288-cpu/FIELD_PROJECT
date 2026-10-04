import { api } from './client';
import type { Prediction, PredictionEvaluation } from './types';

export interface PredictionParams {
  from: string;
  to: string;
  slotMinutes?: number;
  method?: 'empirical' | 'naive';
}

export async function getPrediction(
  equipmentId: number | string,
  params: PredictionParams
): Promise<Prediction> {
  const { data } = await api.get<Prediction>(`/predictions/equipment/${equipmentId}`, {
    params: { slotMinutes: 60, ...params }
  });
  return data;
}

export async function getEvaluation(
  equipmentId: number | string,
  params: PredictionParams
): Promise<PredictionEvaluation> {
  const { data } = await api.get<PredictionEvaluation>(
    `/predictions/equipment/${equipmentId}/evaluation`,
    { params: { slotMinutes: 60, ...params } }
  );
  return data;
}

/** Next `hours` starting now, as ISO strings (matches backend UTC windows). */
export function nextHours(hours: number): { from: string; to: string } {
  const from = new Date();
  const to = new Date(from.getTime() + hours * 3_600_000);
  return { from: from.toISOString(), to: to.toISOString() };
}
