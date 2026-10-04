interface Props {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}

export default function Pagination({ page, totalPages, totalElements, onPage }: Props) {
  if (totalPages <= 1) return <p className="muted">{totalElements} item(s)</p>;
  return (
    <div className="pagination">
      <button className="btn" disabled={page === 0} onClick={() => onPage(page - 1)}>
        ← Prev
      </button>
      <span className="muted">
        Page {page + 1} of {totalPages} · {totalElements} item(s)
      </span>
      <button className="btn" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Next →
      </button>
    </div>
  );
}
