import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getEquipment } from '../api/equipment';
import { getEvaluation, getPrediction } from '../api/predictions';
import { apiMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { Equipment, Prediction, PredictionEvaluation } from '../api/types';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import { barClass, fmtHour, pct, statusLabel } from '../components/predictionLabels';

const HORIZONS = [
  { label: 'Next 24 hours', hours: 24 },
  { label: 'Next 3 days', hours: 72 },
  { label: 'Next 7 days', hours: 168 }
];

const SLOT_SIZES = [30, 60, 120];

/**
 * Predictive availability view. Every number is a forecast with reasons —
 * the page states plainly that predictions never override real bookings.
 */
export default function PredictPage() {
  const { id } = useParams<{ id: string }>();
  const { hasRole } = useAuth();
  const [item, setItem] = useState<Equipment | null>(null);
  const [forecast, setForecast] = useState<Prediction | null>(null);
  const [evaluation, setEvaluation] = useState<PredictionEvaluation | null>(null);
  const [hours, setHours] = useState(24);
  const [slotMinutes, setSlotMinutes] = useState(60);
  const [method, setMethod] = useState<'empirical' | 'naive'>('empirical');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const equipment = await getEquipment(id);
      setItem(equipment);
      const to = new Date();
      const from = new Date(to.getTime() - 0); // window starts now
      const end = new Date(from.getTime() + hours * 3_600_000);
      const res = await getPrediction(id, {
        from: from.toISOString(),
        to: end.toISOString(),
        slotMinutes,
        method
      });
      setForecast(res);
      if (hasRole('VENDOR', 'ADMIN')) {
        const pastEnd = new Date(from.getTime() - 24 * 3_600_000);
        const pastStart = new Date(pastEnd.getTime() - 7 * 24 * 3_600_000);
        try {
          setEvaluation(
            await getEvaluation(id, {
              from: pastStart.toISOString(),
              to: pastEnd.toISOString(),
              slotMinutes
            })
          );
        } catch {
          setEvaluation(null); // thin history: evaluation refuses — not an error state
        }
      }
    } catch (err) {
      setError(apiMessage(err, 'Could not load prediction'));
    } finally {
      setLoading(false);
    }
  }, [id, hours, slotMinutes, method, hasRole]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <Loading label="Computing forecast…" />;
  if (error) return <ErrorAlert message={error} onRetry={load} />;
  if (!item || !forecast) return <EmptyState message="No forecast available." />;

  return (
    <section>
      <p>
        <Link to={`/equipment/${item.id}`}>← Back to {item.name}</Link>
      </p>
      <div className="card">
        <div className="card-row">
          <h1>
            Predicted availability <span className="muted small">{item.equipmentCode}</span>
          </h1>
          <StatusBadge value={item.currentStatus} />
        </div>
        <p className="muted small">
          Predicted availability — <strong>not guaranteed availability</strong>. Confirmed
          bookings and maintenance always take precedence over these numbers.
        </p>
        <div className="filters">
          <select className="input" value={hours} onChange={(e) => setHours(Number(e.target.value))}>
            {HORIZONS.map((h) => (
              <option key={h.hours} value={h.hours}>
                {h.label}
              </option>
            ))}
          </select>
          <select
            className="input"
            value={slotMinutes}
            onChange={(e) => setSlotMinutes(Number(e.target.value))}
          >
            {SLOT_SIZES.map((m) => (
              <option key={m} value={m}>
                {m}-min slots
              </option>
            ))}
          </select>
          <select
            className="input"
            value={method}
            onChange={(e) => setMethod(e.target.value as 'empirical' | 'naive')}
          >
            <option value="empirical">Empirical baseline</option>
            <option value="naive">Naive (current state)</option>
          </select>
        </div>
      </div>

      <ul className="bars">
        {forecast.slots.map((s) => {
          const label = statusLabel(s);
          return (
            <li key={s.startTime} className="slot-row">
              <span className="bar-label">{fmtHour(s.startTime)}</span>
              <span className="bar-track">
                <span
                  className={barClass(s)}
                  style={{ width: `${Math.round(s.probabilityAvailable * 100)}%` }}
                />
              </span>
              <span className="bar-value">{pct(s.probabilityAvailable)}</span>
              <span className={`badge ${label.cls}`}>{label.text}</span>
              <span className="muted small slot-reasons">
                {s.factors.map((f) => f.detail).join(' · ')}
              </span>
            </li>
          );
        })}
      </ul>
      {forecast.slots.length === 0 && <EmptyState message="No slots in this window." />}

      <div className="card">
        <h2>How to read this</h2>
        <p className="muted small">{forecast.disclaimer}</p>
        <p className="muted small">
          Method: {forecast.method} · confidence reflects how much history backs each slot.
          BLOCKED means a confirmed booking already covers the slot; UNAVAILABLE (state)
          means maintenance or sensor outage.
        </p>
        {evaluation && (
          <p className="muted small">
            Backtest (past 7 days, staff view): {evaluation.correct}/{evaluation.slotsEvaluated}{' '}
            slots agree with what happened (accuracy {evaluation.accuracy}, Brier{' '}
            {evaluation.brierScore}). Consistency check — not a future-accuracy claim.
          </p>
        )}
      </div>
    </section>
  );
}
