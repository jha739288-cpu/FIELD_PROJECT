import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { bookingCalendar } from '../api/bookings';
import { apiMessage } from '../api/client';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { CalendarResponse } from '../api/types';

function fmtDay(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'short',
    day: 'numeric'
  });
}

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

function dayKey(iso: string): string {
  return iso.slice(0, 10);
}

/** Week lanes of real reservations from the calendar API. */
export default function CalendarPage() {
  const [params] = useSearchParams();
  const focusId = params.get('equipmentId');
  const [data, setData] = useState<CalendarResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const window = (() => {
    const from = new Date();
    from.setHours(0, 0, 0, 0);
    const to = new Date(from.getTime() + 7 * 86_400_000);
    return { from: from.toISOString(), to: to.toISOString() };
  })();

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(
        await bookingCalendar(
          window.from,
          window.to,
          focusId ? Number(focusId) : undefined
        )
      );
    } catch (err) {
      setError(apiMessage(err, 'Unable to load the calendar. Please try again.'));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusId]);

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load]);

  if (loading) return <Loading label="Loading calendar…" />;
  if (error) return <ErrorAlert message={error} onRetry={load} />;
  if (!data) return <EmptyState message="No calendar data." />;

  const lanes = data.schedules.filter((s) => s.slots.length > 0);
  const days = new Map<string, typeof lanes>();
  for (const lane of lanes) {
    for (const slot of lane.slots) {
      const key = dayKey(slot.startTime);
      if (!days.has(key)) days.set(key, []);
      if (!days.get(key)!.includes(lane)) days.get(key)!.push(lane);
    }
  }
  const sortedDays = [...days.entries()].sort(([a], [b]) => (a < b ? -1 : 1));

  return (
    <section>
      <h1>Booking calendar — next 7 days</h1>
      {lanes.length === 0 && (
        <EmptyState message="Nothing reserved in the coming week. Enjoy the quiet lab." />
      )}
      {sortedDays.map(([day, dayLanes]) => (
        <div key={day} className="card">
          <h2>{fmtDay(`${day}T12:00:00Z`)}</h2>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Equipment</th>
                  <th>Time</th>
                  <th>Status</th>
                  <th>Holder</th>
                </tr>
              </thead>
              <tbody>
                {dayLanes.flatMap((lane) =>
                  lane.slots
                    .filter((s) => dayKey(s.startTime) === day)
                    .map((s) => (
                      <tr key={`${lane.equipmentId}-${s.id}`}>
                        <td>
                          <Link to={`/equipment/${lane.equipmentId}`}>{lane.equipmentName}</Link>
                          <div className="muted small">{lane.equipmentCode}</div>
                        </td>
                        <td>
                          {fmtTime(s.startTime)}–{fmtTime(s.endTime)}
                        </td>
                        <td>
                          <StatusBadge value={s.status} />
                        </td>
                        <td>{s.username ?? <span className="muted">reserved</span>}</td>
                      </tr>
                    ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </section>
  );
}
