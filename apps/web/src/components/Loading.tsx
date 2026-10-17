import { messages } from '../messages';

export function Loading() {
  return (
    <p role="status" className="muted">
      {messages.loading}
    </p>
  );
}
