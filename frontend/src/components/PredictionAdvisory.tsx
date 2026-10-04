import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getPrediction, nextHours } from '../api/predictions';
import { apiMessage } from '../api/client';

/**
 * Booking-screen advisory: the single best-looking slot in the next 24h.
 * Advisory only — booking validation stays entirely server-side.
 */
export default function PredictionAdvisory({ equipmentId }: { equipmentId: number }) {
  const [text, setText] = useState('Checking historical availability…');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let live = true;
    const window = nextHours(24);
    getPrediction(equipmentId, { ...window, slotMinutes: 60 })
      .then((res) => {
        if (!live) return;
        const free = res.slots.filter((s) => s.predictedStatus === 'AVAILABLE');
        if (free.length === 0) {
          const blocked = res.slots.filter((s) =>
            s.factors.some((f) => f.name === 'existing_booking')
          ).length;
          setText(
            blocked > 0
              ? `Historically busy: ${blocked} of the next 24 hourly slots already have bookings. Check the calendar.`
              : 'No historically free slot stands out in the next 24 hours — check the calendar.'
          );
          return;
        }
        const best = free.reduce((a, b) =>
          b.probabilityAvailable > a.probabilityAvailable ? b : a
        );
        const start = new Date(best.startTime).toLocaleString(undefined, {
          weekday: 'short',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false
        });
        setText(
          `Historical prediction: ${Math.round(best.probabilityAvailable * 100)}% likely ` +
            `available around ${start} (advisory only — the booking check decides).`
        );
      })
      .catch((err: unknown) => {
        if (!live) return;
        setFailed(true);
        setText(apiMessage(err, 'Prediction unavailable'));
      });
    return () => {
      live = false;
    };
  }, [equipmentId]);

  return (
    <p className={`advisory${failed ? ' advisory-error' : ''}`}>
      <span aria-hidden>◷ </span>
      {text}{' '}
      <Link to={`/equipment/${equipmentId}/predict`}>Predict availability</Link>
    </p>
  );
}
