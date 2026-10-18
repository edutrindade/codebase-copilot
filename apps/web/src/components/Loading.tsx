import { messages } from '../messages';
import { Skeleton } from './ui/Skeleton';

const LINE_WIDTHS = ['60%', '92%', '78%', '85%', '40%'];

export function Loading({ lines = 3 }: { lines?: number }) {
  return (
    <div role="status" className="loading">
      <span className="visually-hidden">{messages.loading}</span>
      {LINE_WIDTHS.slice(0, lines).map((width) => (
        <Skeleton key={width} width={width} />
      ))}
    </div>
  );
}
