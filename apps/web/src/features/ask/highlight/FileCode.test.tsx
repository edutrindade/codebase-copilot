import { render, screen } from '@testing-library/react';
import { afterAll, beforeAll, describe, expect, test, vi } from 'vitest';
import { messages } from '../../../messages';
import { axeViolations } from '../../../test/axe';
import { InMain } from '../../../test/render';
import { FileCode } from './FileCode';
import type { HighlightRequest } from './highlight.worker';
import { tokenize } from './tokenize';

const SOURCE = 'class A {\n  String s = "x";\n}\n';

// jsdom has no Worker: this one tokenizes on the main thread, or never answers.
class FakeWorker {
  static hang = false;
  static terminated = 0;
  onmessage: ((event: MessageEvent) => void) | null = null;

  postMessage(request: HighlightRequest) {
    if (FakeWorker.hang) {
      return;
    }
    void tokenize(request.language, request.text).then((tokens) =>
      this.onmessage?.({ data: { id: request.id, ...tokens } } as MessageEvent),
    );
  }

  terminate() {
    FakeWorker.terminated++;
  }
}

beforeAll(() => {
  vi.stubGlobal('Worker', FakeWorker);
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
    configurable: true,
    get(this: HTMLElement) {
      return this.classList.contains('code-view') ? 400 : 0;
    },
  });
});

afterAll(() => {
  vi.unstubAllGlobals();
  Reflect.deleteProperty(HTMLElement.prototype, 'clientHeight');
});

function renderFile(path: string, content = SOURCE) {
  return render(
    <InMain>
      <FileCode path={path} content={content} highlight={null} label="Código" />
    </InMain>,
  );
}

describe('FileCode', () => {
  test('shows the plain text at once and the colors when the worker answers', async () => {
    const { container } = renderFile('src/A.java');

    expect(screen.getByRole('region', { name: 'Código' })).toHaveTextContent('String s = "x";');
    expect(container.querySelector('.tok-keyword')).toBeNull();
    expect(await screen.findByText('"x"')).toHaveClass('tok-string');
    expect(container.querySelector('.tok-keyword')).toHaveTextContent('class');
    expect(screen.getByRole('region', { name: 'Código' })).toHaveTextContent('String s = "x";');
    expect(await axeViolations()).toEqual([]);
  });

  test('an unknown language stays plain without asking the worker', () => {
    const { container } = renderFile('db/V1__init.sql', 'select 1;\n');

    expect(screen.getByText('select 1;')).toBeInTheDocument();
    expect(container.querySelector('[class^="tok-"]')).toBeNull();
  });

  test('a parse past the time limit restarts the worker and keeps the text plain', async () => {
    FakeWorker.hang = true;
    const { container } = renderFile('src/Slow.java');

    expect(
      await screen.findByText(messages.ask.viewer.highlightTimedOut, {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(FakeWorker.terminated).toBe(1);
    expect(container.querySelector('[class^="tok-"]')).toBeNull();
    FakeWorker.hang = false;
  });
});
