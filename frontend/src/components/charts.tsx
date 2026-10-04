/** Minimal dependency-free SVG charts. All scales derive from the data passed in. */

const COLORS = ['#1d4ed8', '#0ea5e9', '#f59e0b', '#ef4444', '#6b7280', '#10b981', '#8b5cf6'];

export function DonutChart({ slices }: { slices: Array<{ label: string; value: number }> }) {
  const total = slices.reduce((a, s) => a + s.value, 0);
  const R = 54;
  const C = 2 * Math.PI * R;
  let offset = 25;
  return (
    <div className="chart-row">
      <svg viewBox="0 0 140 140" width="140" height="140" role="img" aria-label="Status mix">
        <circle cx="70" cy="70" r={R} fill="none" stroke="#eef1f5" strokeWidth="22" />
        {slices.map((s, i) => {
          const frac = total === 0 ? 0 : s.value / total;
          const el = (
            <circle
              key={s.label}
              cx="70"
              cy="70"
              r={R}
              fill="none"
              stroke={COLORS[i % COLORS.length]}
              strokeWidth="22"
              strokeDasharray={`${(frac * C).toFixed(1)} ${C.toFixed(1)}`}
              strokeDashoffset={(-offset * C).toFixed(1)}
            />
          );
          offset += frac;
          return el;
        })}
        <text x="70" y="76" textAnchor="middle" fontSize="22" fontWeight="700">
          {total}
        </text>
      </svg>
      <ul className="legend">
        {slices.map((s, i) => (
          <li key={s.label}>
            <span className="swatch" style={{ background: COLORS[i % COLORS.length] }} />
            {s.label}: <strong>{s.value}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function BarList({ items }: { items: Array<{ label: string; value: number; hint?: string }> }) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <ul className="bars">
      {items.map((i) => (
        <li key={i.label}>
          <span className="bar-label" title={i.hint ?? i.label}>
            {i.label}
          </span>
          <span className="bar-track">
            <span className="bar-fill" style={{ width: `${(100 * i.value) / max}%` }} />
          </span>
          <span className="bar-value">{i.value}</span>
        </li>
      ))}
    </ul>
  );
}

export function TrendChart({
  points
}: {
  points: Array<{ date: string; usageHours: number; bookingsCreated: number }>;
}) {
  const W = 560;
  const H = 180;
  const PAD = 28;
  const maxHours = Math.max(0.5, ...points.map((p) => p.usageHours));
  const maxCount = Math.max(1, ...points.map((p) => p.bookingsCreated));
  const n = Math.max(1, points.length);
  const x = (i: number) => PAD + (i * (W - 2 * PAD)) / Math.max(1, n - 1);
  const yHours = (v: number) => H - PAD - (v / maxHours) * (H - 2 * PAD);
  const yCount = (v: number) => H - PAD - (v / maxCount) * (H - 2 * PAD);
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${yHours(p.usageHours).toFixed(1)}`).join(' ');
  return (
    <figure className="trend">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Usage trend">
        {[0.25, 0.5, 0.75].map((f) => (
          <line
            key={f}
            x1={PAD}
            x2={W - PAD}
            y1={PAD + f * (H - 2 * PAD)}
            y2={PAD + f * (H - 2 * PAD)}
            stroke="#eef1f5"
          />
        ))}
        {points.map((p, i) => (
          <rect
            key={p.date}
            x={x(i) - 5}
            y={yCount(p.bookingsCreated)}
            width={10}
            height={Math.max(0, H - PAD - yCount(p.bookingsCreated))}
            fill="#bfdbfe"
          />
        ))}
        <path d={`${line}`} fill="none" stroke="#1d4ed8" strokeWidth="2.5" />
        {points.map((p, i) => (
          <circle key={p.date} cx={x(i)} cy={yHours(p.usageHours)} r="3" fill="#1d4ed8">
            <title>{`${p.date}: ${p.usageHours}h, ${p.bookingsCreated} bookings`}</title>
          </circle>
        ))}
      </svg>
      <figcaption className="muted small">
        Line: usage hours (left scale, max {maxHours.toFixed(1)}h) · Bars: bookings created (max{' '}
        {maxCount}) · {points.length} day(s)
      </figcaption>
    </figure>
  );
}
