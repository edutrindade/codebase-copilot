import { SearchX } from 'lucide-react';
import { ButtonLink } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { messages } from '../../messages';

export function NotFoundPage() {
  return (
    <section>
      <EmptyState
        icon={SearchX}
        title={messages.notFound.title}
        headingLevel={1}
        action={<ButtonLink to="/">{messages.notFound.back}</ButtonLink>}
      >
        <p>{messages.notFound.detail}</p>
      </EmptyState>
    </section>
  );
}
