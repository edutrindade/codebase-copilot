import { useVirtualizer } from '@tanstack/react-virtual';
import { useEffect, useRef } from 'react';
import { messages } from '../../messages';
import type { LineRange } from './evidence';

// Must match the row height in .code-view__line; rows never wrap, so every row has this height.
const LINE_HEIGHT_PX = 20;

interface CodeLinesProps {
  lines: readonly string[];
  firstLine: number;
  highlight: LineRange | null;
  label: string;
}

// Always virtualized (P7): files reach tens of thousands of lines.
export function CodeLines({ lines, firstLine, highlight, label }: CodeLinesProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  // The build has no React Compiler, so nothing memoizes around the virtualizer.
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({
    count: lines.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => LINE_HEIGHT_PX,
    overscan: 20,
  });
  const scrollTarget = highlight ? highlight.start - firstLine : null;

  useEffect(() => {
    if (scrollTarget !== null && scrollTarget >= 0 && scrollTarget < lines.length) {
      virtualizer.scrollToIndex(scrollTarget, { align: 'center' });
    }
  }, [scrollTarget, lines.length, virtualizer]);

  const items = virtualizer.getVirtualItems();
  const before = items[0]?.start ?? 0;
  const after = virtualizer.getTotalSize() - (items[items.length - 1]?.end ?? 0);

  return (
    <div ref={scrollRef} className="code-view" role="region" aria-label={label} tabIndex={0}>
      <div className="code-view__lines" style={{ paddingTop: before, paddingBottom: after }}>
        {items.map((item) => {
          const number = firstLine + item.index;
          const cited = highlight !== null && number >= highlight.start && number <= highlight.end;
          return (
            <div
              key={item.key}
              className={cited ? 'code-view__line code-view__line--cited' : 'code-view__line'}
              data-line={number}
            >
              <span className="code-view__number">{number}</span>
              <span className="code-view__marker" aria-hidden="true">
                {cited ? '▶' : ''}
              </span>
              {cited && <span className="visually-hidden">{messages.ask.viewer.citedMarker}</span>}
              <span className="code-view__text">{lines[item.index]}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
