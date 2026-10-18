import { Fragment, type ReactNode } from 'react';
import { CopyButton } from '../../components/ui/CopyButton';
import { HoverCard } from '../../components/ui/HoverCard';
import { messages } from '../../messages';
import type { Evidence } from './api';
import { formatAnswer, type Block, type Inline } from './answerFormat';
import { highlightedRange, splitLines } from './evidence';

const PREVIEW_LINES = 4;

interface AnswerContentProps {
  text: string;
  evidence: readonly Evidence[];
  onCite: (evidence: Evidence) => void;
}

export function AnswerContent({ text, evidence, onCite }: AnswerContentProps) {
  const byLabel = new Map(evidence.map((item) => [item.citationLabel, item]));
  const blocks = formatAnswer(text, new Set(byLabel.keys()));

  function inline(nodes: readonly Inline[]): ReactNode[] {
    return nodes.map((node, index) => {
      switch (node.type) {
        case 'text':
          return <Fragment key={index}>{node.text}</Fragment>;
        case 'break':
          return <br key={index} />;
        case 'code':
          return <code key={index}>{node.text}</code>;
        case 'strong':
          return <strong key={index}>{inline(node.children)}</strong>;
        case 'citation': {
          const cited = byLabel.get(node.label);
          return cited && <Citation key={index} evidence={cited} onCite={onCite} />;
        }
      }
    });
  }

  function block(item: Block, index: number): ReactNode {
    switch (item.type) {
      case 'paragraph':
        return <p key={index}>{inline(item.content)}</p>;
      case 'list': {
        const items = item.items.map((content, position) => (
          <li key={position}>{inline(content)}</li>
        ));
        return item.ordered ? (
          <ol key={index} start={item.start === 1 ? undefined : item.start}>
            {items}
          </ol>
        ) : (
          <ul key={index}>{items}</ul>
        );
      }
      case 'code':
        return <CodeBlock key={index} language={item.language} code={item.code} />;
    }
  }

  return <div className="answer-text">{blocks.map(block)}</div>;
}

function Citation({
  evidence,
  onCite,
}: {
  evidence: Evidence;
  onCite: (evidence: Evidence) => void;
}) {
  const text = messages.ask;
  const range = highlightedRange(evidence);
  const lines = text.lines(range.start, range.end);
  return (
    <HoverCard content={<CitationPreview evidence={evidence} />}>
      <button
        type="button"
        className="citation"
        aria-label={text.citation(evidence.citationLabel, evidence.path, lines)}
        onClick={() => onCite(evidence)}
      >
        {evidence.citationLabel}
      </button>
    </HoverCard>
  );
}

function CitationPreview({ evidence }: { evidence: Evidence }) {
  const range = highlightedRange(evidence);
  const offset = Math.max(0, range.start - evidence.startLine);
  const preview = splitLines(evidence.snippet).slice(offset, offset + PREVIEW_LINES);
  return (
    <>
      <p className="citation-preview__path">
        <span className="viewer__label">{evidence.citationLabel}</span> <code>{evidence.path}</code>
      </p>
      <p className="muted">{messages.ask.lines(range.start, range.end)}</p>
      <pre className="citation-preview__code">
        {preview.map((line, index) => (
          <span key={index} className="citation-preview__line">
            <span className="citation-preview__number">{range.start + index}</span>
            {line}
          </span>
        ))}
      </pre>
    </>
  );
}

function CodeBlock({ language, code }: { language: string | null; code: string }) {
  const text = messages.ask;
  return (
    <div className="answer-code">
      <div className="answer-code__header">
        <span>{language ?? text.codeBlock}</span>
        <CopyButton text={code} label={text.copyCode} />
      </div>
      {/* Focusable so a long line can be scrolled by keyboard. */}
      <pre className="answer-code__body" tabIndex={0}>
        <code>{code}</code>
      </pre>
    </div>
  );
}
