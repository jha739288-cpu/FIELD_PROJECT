const STATUS_CLASS: Record<string, string> = {
  AVAILABLE: 'badge-green',
  RESERVED: 'badge-amber',
  IN_USE: 'badge-blue',
  OVERDUE: 'badge-red',
  MAINTENANCE: 'badge-gray',
  SENSOR_OFFLINE: 'badge-dark',
  OPERATIONAL: 'badge-green',
  DUE: 'badge-amber',
  IN_MAINTENANCE: 'badge-gray',
  OUT_OF_SERVICE: 'badge-red',
  NEW: 'badge-green',
  GOOD: 'badge-green',
  FAIR: 'badge-amber',
  POOR: 'badge-red',
  DAMAGED: 'badge-red',
  PENDING: 'badge-amber',
  CONFIRMED: 'badge-blue',
  CHECKED_IN: 'badge-blue',
  COMPLETED: 'badge-green',
  CANCELLED: 'badge-gray',
  OVERDUE_BOOKING: 'badge-red',
  REJECTED: 'badge-red'
};

export default function StatusBadge({ value }: { value: string }) {
  const cls = STATUS_CLASS[value] ?? 'badge-gray';
  return <span className={`badge ${cls}`}>{value.replace(/_/g, ' ')}</span>;
}
