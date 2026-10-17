import { messages } from '../messages';

interface PaginationProps {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label={messages.pagination.label}>
      <button
        type="button"
        className="button"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        {messages.pagination.previous}
      </button>
      <span>{messages.pagination.status(page + 1, totalPages)}</span>
      <button
        type="button"
        className="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        {messages.pagination.next}
      </button>
    </nav>
  );
}

export function pageFromSearch(params: URLSearchParams): number {
  const page = Number(params.get('page') ?? '1');
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0;
}
