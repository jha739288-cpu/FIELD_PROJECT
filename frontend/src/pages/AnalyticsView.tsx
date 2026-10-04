import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getBookingAnalytics,
  getConflictCount,
  getEquipmentDashboard,
  getSensorAnalytics,
  getSummary,
  getUsageTrend,
  getUtilization,
  trailingDays
} from '../api/dashboard';
import { apiMessage } from '../api/client';
import type {
  BookingAnalytics,
  EquipmentDashboard,
  SensorAnalytics,
  Summary,
  UsageTrend,
  Utilization
} from '../api/types';
import { BarList, DonutChart, TrendChart } from '../components/charts';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';

function StatCard({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="stat">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {hint && <div className="muted small">{hint}</div>}
    </div>
  );
}

/**
 * Full analytics view: current-status snapshot, calculated utilization and
 * historical usage — each section labelled with what it represents.
 */
export default function AnalyticsView() {
  const [days, setDays] = useState(30);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [util, setUtil] = useState<Utilization | null>(null);
  const [trend, setTrend] = useState<UsageTrend | null>(null);
  const [conflicts, setConflicts] = useState<number | null>(null);
  const [bookingStats, setBookingStats] = useState<BookingAnalytics | null>(null);
  const [sensorStats, setSensorStats] = useState<SensorAnalytics | null>(null);
  const [focusId, setFocusId] = useState<string>('');
  const [focus, setFocus] = useState<EquipmentDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const window = trailingDays(days);
      const [s, u, t, c, b, se] = await Promise.all([
        getSummary(),
        getUtilization(window.from!, window.to!),
        getUsageTrend(window),
        getConflictCount(window),
        getBookingAnalytics(window),
        getSensorAnalytics(window)
      ]);
      setSummary(s);
      setUtil(u);
      setTrend(t);
      setConflicts(c);
      setBookingStats(b);
      setSensorStats(se);
    } catch (err) {
      setError(apiMessage(err, 'Could not load analytics'));
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!focusId) {
      setFocus(null);
      return;
    }
    const window = trailingDays(days);
    getEquipmentDashboard(focusId, window)
      .then(setFocus)
      .catch(() => setFocus(null));
  }, [focusId, days]);

  if (loading) return <Loading label="Computing analytics…" />;
  if (error) return <ErrorAlert message={error} onRetry={load} />;
  if (!summary || !util || !trend || conflicts === null || !bookingStats || !sensorStats)
    return null;

  if (summary.equipment.total === 0) {
    return (
      <div>
        <div className="page-head">
          <h1>Fleet analytics</h1>
        </div>
        <EmptyState message="No equipment registered yet — analytics will appear once the catalog has items and activity." />
        <div className="actions">
          <Link className="btn btn-primary" to="/equipment">
            Open catalog
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="page-head">
        <h1>Fleet analytics</h1>
        <label className="muted small">
          Window{' '}
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
          </select>
        </label>
      </div>

      <h2>Current status — live snapshot</h2>
      <div className="stats">
        <StatCard label="Equipment total" value={summary.equipment.total} />
        <StatCard label="Available now" value={summary.equipment.available} />
        <StatCard label="In use now" value={summary.equipment.inUse} />
        <StatCard label="Maintenance now" value={summary.equipment.maintenance} />
        <StatCard label="Open alerts" value={summary.alerts.open} />
      </div>
      <div className="card">
        <h2>Equipment mix</h2>
        <DonutChart
          slices={[
            { label: 'Available', value: summary.equipment.available },
            { label: 'Reserved', value: summary.equipment.reserved },
            { label: 'In use', value: summary.equipment.inUse },
            { label: 'Overdue', value: summary.equipment.overdue },
            { label: 'Maintenance', value: summary.equipment.maintenance },
            { label: 'Sensor offline', value: summary.equipment.sensorOffline }
          ]}
        />
      </div>

      <h2>Calculated utilization — usage ÷ fleet capacity over the window</h2>
      <div className="card">
        <p>
          <strong>{util.utilizationPercent}%</strong> overall (
          {(util.totalUsageSeconds / 3600).toFixed(1)}h used of{' '}
          {(util.windowSeconds * util.operationalCount / 3600).toFixed(0)}h capacity,{' '}
          {util.operationalCount} operational of {util.equipmentCount} item(s))
        </p>
        <BarList
          items={util.items.map((i) => ({
            label: `${i.equipmentCode} · ${i.equipmentName}${i.operational ? '' : ' (non-operational)'}`,
            value: i.utilizationPercent,
            hint: `${(i.usageSeconds / 3600).toFixed(1)}h`
          }))}
        />
        <p className="muted small">
          Bars show % per item; hover for hours. Sorted most-used first. Items out of
          service are listed but excluded from capacity.
        </p>
        <Link className="btn" to="/equipment">
          Open catalog
        </Link>
      </div>

      <h2>Bookings — status mix and volume</h2>
      <div className="card">
        <DonutChart
          slices={Object.entries(bookingStats.byStatus).map(([label, value]) => ({
            label,
            value
          }))}
        />
        <p className="muted small">
          {bookingStats.total} booking(s) created in the window · {bookingStats.cancelled}{' '}
          cancelled · {bookingStats.overdue} overdue.
        </p>
      </div>

      <h2>Most-used equipment</h2>
      <div className="card">
        {util.items.filter((i) => i.usageSeconds > 0).length === 0 ? (
          <EmptyState message="No recorded usage in this window." />
        ) : (
          <BarList
            items={util.items
              .filter((i) => i.usageSeconds > 0)
              .slice(0, 5)
              .map((i) => ({
                label: `${i.equipmentCode} · ${i.equipmentName}`,
                value: Number((i.usageSeconds / 3600).toFixed(1)),
                hint: `${i.utilizationPercent}% of window`
              }))}
          />
        )}
        <label className="muted small">
          Inspect item{' '}
          <select value={focusId} onChange={(e) => setFocusId(e.target.value)}>
            <option value="">— select —</option>
            {util.items.map((i) => (
              <option key={i.equipmentId} value={i.equipmentId}>
                {i.equipmentCode} · {i.equipmentName}
              </option>
            ))}
          </select>
        </label>
        {focus && (
          <p className="small">
            <strong>
              {focus.equipmentCode} · {focus.name}
            </strong>{' '}
            — {focus.currentStatus}, {(focus.usageSeconds / 3600).toFixed(1)}h used (
            {focus.utilizationPercent}%), {focus.sessions} session(s),{' '}
            {focus.completedSessions} completed,{' '}
            {Object.entries(focus.bookingsByStatus)
              .map(([k, v]) => `${v} ${k}`)
              .join(' · ') || 'no bookings'}
            .
          </p>
        )}
      </div>

      <h2>Sensors — device reports in the window</h2>
      <div className="stats">
        <StatCard label="Events" value={sensorStats.total} />
        <StatCard
          label="Freshness"
          value={sensorStats.reliability === null ? '—' : `${(sensorStats.reliability * 100).toFixed(0)}%`}
          hint="on-time share of stored events"
        />
        <StatCard label="Faults" value={sensorStats.faults} />
        <StatCard label="Offline" value={sensorStats.offline} />
        <StatCard label="Stale" value={sensorStats.stale} />
      </div>
      {sensorStats.byEquipment.length > 0 && (
        <div className="card">
          <h2>Events by equipment</h2>
          <BarList
            items={sensorStats.byEquipment.map((e) => ({
              label: e.equipmentCode,
              value: e.count
            }))}
          />
        </div>
      )}

      <h2>Overdue alerts</h2>
      <div className="card">
        <p>
          <strong>{summary.alerts.open}</strong> open ·{' '}
          <strong>{summary.alerts.resolved}</strong> resolved
        </p>
        <p className="muted small">
          Raised once per overdue booking by the scheduler; staff resolve them from the
          operations workflow.
        </p>
      </div>

      <h2>Historical usage — what already happened</h2>
      <div className="stats">
        <StatCard label="Usage hours (window)" value={summary.usage.totalHours} />
        <StatCard label="Sessions completed" value={summary.usage.sessionsCompleted} />
        <StatCard label="Bookings completed" value={summary.bookings.completed} />
        <StatCard label="Cancelled" value={summary.bookings.cancelled} />
        <StatCard label="Overdue ever" value={summary.bookings.overdue} />
        <StatCard label="Conflict attempts" value={conflicts} hint="rejected overlaps" />
        <StatCard label="Sensor events" value={summary.sensors.events} />
        <StatCard label="Sensor faults" value={summary.sensors.faults} />
        <StatCard label="Stale reports" value={summary.sensors.staleEvents} />
      </div>
      <div className="card">
        <h2>Daily trend</h2>
        <TrendChart points={trend.points} />
      </div>
    </div>
  );
}
