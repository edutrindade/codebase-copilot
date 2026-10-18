import { HoverCard as RadixHoverCard } from 'radix-ui';
import type { ReactElement, ReactNode } from 'react';
import './HoverCard.css';

interface HoverCardProps {
  content: ReactNode;
  children: ReactElement;
  side?: 'top' | 'right' | 'bottom' | 'left';
}

// A preview on hover or focus, for sighted users: the trigger's own name must already say it all.
export function HoverCard({ content, children, side = 'top' }: HoverCardProps) {
  return (
    <RadixHoverCard.Root openDelay={300} closeDelay={150}>
      <RadixHoverCard.Trigger asChild>{children}</RadixHoverCard.Trigger>
      <RadixHoverCard.Portal>
        <RadixHoverCard.Content
          className="hover-card"
          aria-hidden="true"
          side={side}
          sideOffset={6}
          collisionPadding={8}
        >
          {content}
        </RadixHoverCard.Content>
      </RadixHoverCard.Portal>
    </RadixHoverCard.Root>
  );
}
