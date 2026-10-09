export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <p className="muted">{label}</p>;
}

export function ErrorAlert({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button className="btn btn-ghost" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <p className="muted">{message}</p>;
}

export function SkeletonCard() {
  return (
    <div className="card skeleton-card" aria-hidden>
      <div className="skeleton skeleton-line" style={{ width: '60%' }} />
      <div className="skeleton skeleton-line" style={{ width: '85%' }} />
      <div className="skeleton skeleton-line" style={{ width: '40%' }} />
    </div>
  );
}

export function SkeletonGrid({ count = 6 }: { count?: number }) {
  return (
    <div className="grid">
      {Array.from({ length: count }, (_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  );
}
