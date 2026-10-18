import { Fragment } from 'react';
import type { Highlighting } from './Highlighting';
import './syntax.css';

export function LineText({
  text,
  highlighting,
  line,
}: {
  text: string;
  highlighting: Highlighting | null;
  line: number;
}) {
  if (!highlighting) {
    return text;
  }
  return highlighting.segments(line).map((segment, index) =>
    segment.className ? (
      <span key={index} className={segment.className}>
        {segment.text}
      </span>
    ) : (
      <Fragment key={index}>{segment.text}</Fragment>
    ),
  );
}
