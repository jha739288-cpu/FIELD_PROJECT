import type { PredictionSlot } from '../api/types';

/** Project convention: backend statuses rendered as user-facing labels. */
export function statusLabel(slot: PredictionSlot): { text: string; cls: string } {
  if (slot.factors.some((f) => f.name === 'insufficient_history')) {
    return { text: 'INSUFFICIENT DATA', cls: 'badge-gray' };
  }
  if (slot.factors.some((f) => f.name === 'existing_booking')) {
    return { text: 'BLOCKED — BOOKED', cls: 'badge-red' };
  }
  if (slot.factors.some((f) => f.name === 'equipment_state')) {
    return { text: 'UNAVAILABLE — STATE', cls: 'badge-red' };
  }
  switch (slot.predictedStatus) {
    case 'AVAILABLE':
      return { text: 'HIGH', cls: 'badge-green' };
    case 'LIMITED':
      return { text: 'MEDIUM', cls: 'badge-amber' };
    default:
      return { text: 'LOW', cls: 'badge-red' };
  }
}

export function barClass(slot: PredictionSlot): string {
  const label = statusLabel(slot).text;
  if (label === 'HIGH') return 'bar-fill fill-green';
  if (label === 'MEDIUM') return 'bar-fill fill-amber';
  if (label === 'INSUFFICIENT DATA') return 'bar-fill fill-gray';
  return 'bar-fill fill-red';
}

export function fmtHour(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

export function pct(p: number): string {
  return `${Math.round(p * 100)}%`;
}
