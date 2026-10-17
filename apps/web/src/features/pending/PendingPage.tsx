import { messages } from '../../messages';

export function PendingPage({ title }: { title: string }) {
  return (
    <section>
      <h1>{title}</h1>
      <p className="muted">{messages.pending}</p>
    </section>
  );
}
