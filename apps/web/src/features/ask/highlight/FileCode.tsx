import { messages } from '../../../messages';
import { CodeLines } from '../CodeLines';
import { splitLines, type LineRange } from '../evidence';
import { languageForPath } from './languages';
import { useHighlight } from './useHighlight';

interface FileCodeProps {
  path: string;
  content: string;
  firstLine?: number;
  highlight: LineRange | null;
  label: string;
  scrollRequest?: number;
}

// A whole file or a snippet, colored when its language is known.
export function FileCode({ path, content, firstLine = 1, ...props }: FileCodeProps) {
  const state = useHighlight(languageForPath(path), content);
  return (
    <>
      {state.status === 'timedOut' && (
        <p className="muted viewer__notice">{messages.ask.viewer.highlightTimedOut}</p>
      )}
      <CodeLines
        lines={splitLines(content)}
        firstLine={firstLine}
        highlighting={state.status === 'ready' ? state.highlighting : null}
        {...props}
      />
    </>
  );
}
