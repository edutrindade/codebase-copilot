import { Tooltip as RadixTooltip } from 'radix-ui';
import type { ReactElement, ReactNode } from 'react';
import './Tooltip.css';

interface TooltipProps {
  content: ReactNode;
  children: ReactElement;
  side?: 'top' | 'right' | 'bottom' | 'left';
}

// Supplementary only: the trigger keeps its own accessible name.
export function Tooltip({ content, children, side = 'top' }: TooltipProps) {
  return (
    <RadixTooltip.Provider delayDuration={400} skipDelayDuration={200}>
      <RadixTooltip.Root>
        <RadixTooltip.Trigger asChild>{children}</RadixTooltip.Trigger>
        <RadixTooltip.Portal>
          <RadixTooltip.Content className="tooltip" side={side} sideOffset={6} collisionPadding={8}>
            {content}
          </RadixTooltip.Content>
        </RadixTooltip.Portal>
      </RadixTooltip.Root>
    </RadixTooltip.Provider>
  );
}
