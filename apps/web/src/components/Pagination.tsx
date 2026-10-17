import { ChevronLeft, ChevronRight } from 'lucide-react';
import { messages } from '../messages';
import { Button } from './ui/Button';

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
      <Button size="sm" icon={ChevronLeft} disabled={page <= 0} onClick={() => onChange(page - 1)}>
        {messages.pagination.previous}
      </Button>
      <span className="tabular">{messages.pagination.status(page + 1, totalPages)}</span>
      <Button size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        {messages.pagination.next}
        <ChevronRight aria-hidden="true" className="button__icon" />
      </Button>
    </nav>
  );
}

export function pageFromSearch(params: URLSearchParams): number {
  const page = Number(params.get('page') ?? '1');
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0;
}
