import { Link } from 'react-router';
import { messages } from '../messages';

export function RouteError() {
  return (
    <section role="alert">
      <h1>{messages.errors.renderTitle}</h1>
      <p>{messages.errors.renderDetail}</p>
      <Link to="/">{messages.notFound.back}</Link>
    </section>
  );
}
