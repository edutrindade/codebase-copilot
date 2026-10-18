import { TriangleAlert } from 'lucide-react';
import { ButtonLink } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { messages } from '../messages';
import { usePageTitle } from './title';

export function RouteError() {
  usePageTitle([messages.errors.renderTitle]);
  return (
    <section role="alert">
      <EmptyState
        icon={TriangleAlert}
        title={messages.errors.renderTitle}
        headingLevel={1}
        action={<ButtonLink to="/">{messages.notFound.back}</ButtonLink>}
      >
        <p>{messages.errors.renderDetail}</p>
      </EmptyState>
    </section>
  );
}
