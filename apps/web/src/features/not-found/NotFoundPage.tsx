import { Link } from 'react-router';
import { messages } from '../../messages';

export function NotFoundPage() {
  return (
    <section>
      <h1>{messages.notFound.title}</h1>
      <p>{messages.notFound.detail}</p>
      <Link to="/">{messages.notFound.back}</Link>
    </section>
  );
}
