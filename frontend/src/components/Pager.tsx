interface Props {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}

export function Pager({ page, totalPages, onPage }: Props) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pages" className="mt-8 flex items-center justify-center gap-3 text-sm">
      <button type="button" className="btn-quiet" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Previous
      </button>
      <span className="text-ink-soft">
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" className="btn-quiet" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
        Next
      </button>
    </nav>
  );
}
