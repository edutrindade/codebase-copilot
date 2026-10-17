import { formatDateTime, formatRelativeTime } from '../../format';
import { Tooltip } from './Tooltip';

// The exact date is in the tooltip for pointers and in hidden text for screen readers.
export function RelativeTime({ iso }: { iso: string }) {
  const exact = formatDateTime(iso);
  return (
    <Tooltip content={exact}>
      <time dateTime={iso}>
        {formatRelativeTime(iso)}
        <span className="visually-hidden"> ({exact})</span>
      </time>
    </Tooltip>
  );
}
