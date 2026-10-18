import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useRef, type KeyboardEvent, type PointerEvent, type RefObject } from 'react';
import { IconButton } from './IconButton';
import './Splitter.css';

interface SplitterProps {
  // Share of the container, in percent, taken by the pane before the splitter.
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (value: number) => void;
  containerRef: RefObject<HTMLElement | null>;
  controls: string;
  label: string;
  valueText: (value: number) => string;
  decreaseLabel: string;
  increaseLabel: string;
}

// Vertical divider between two columns: drag, arrow keys, or the two buttons (WCAG 2.5.7).
export function Splitter({
  value,
  min,
  max,
  step = 5,
  onChange,
  containerRef,
  controls,
  label,
  valueText,
  decreaseLabel,
  increaseLabel,
}: SplitterProps) {
  const dragging = useRef(false);
  const clamp = (next: number) => Math.round(Math.min(max, Math.max(min, next)));

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const next = {
      ArrowLeft: value - step,
      ArrowDown: value - step,
      ArrowRight: value + step,
      ArrowUp: value + step,
      Home: min,
      End: max,
    }[event.key];
    if (next !== undefined) {
      event.preventDefault();
      onChange(clamp(next));
    }
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    if (event.button !== 0) {
      return;
    }
    dragging.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!dragging.current || !rect || rect.width === 0) {
      return;
    }
    onChange(clamp(((event.clientX - rect.left) / rect.width) * 100));
  }

  function stopDragging(event: PointerEvent<HTMLDivElement>) {
    dragging.current = false;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }

  return (
    <div className="splitter">
      <div className="splitter__inner">
        <IconButton
          label={decreaseLabel}
          icon={ChevronLeft}
          size="sm"
          disabled={value <= min}
          onClick={() => onChange(clamp(value - 2 * step))}
        />
        <IconButton
          label={increaseLabel}
          icon={ChevronRight}
          size="sm"
          disabled={value >= max}
          onClick={() => onChange(clamp(value + 2 * step))}
        />
        <div
          role="separator"
          tabIndex={0}
          className="splitter__handle"
          aria-orientation="vertical"
          aria-label={label}
          aria-controls={controls}
          aria-valuenow={value}
          aria-valuemin={min}
          aria-valuemax={max}
          aria-valuetext={valueText(value)}
          onKeyDown={onKeyDown}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={stopDragging}
          onPointerCancel={stopDragging}
        />
      </div>
    </div>
  );
}
