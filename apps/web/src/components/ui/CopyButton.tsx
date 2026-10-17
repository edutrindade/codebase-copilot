import { Check, Copy } from 'lucide-react';
import { useEffect, useState } from 'react';
import { messages } from '../../messages';
import { Button, type ButtonSize } from './Button';
import { IconButton } from './IconButton';

interface CopyButtonProps {
  text: string;
  label: string;
  size?: ButtonSize;
  // With the label visible instead of an icon-only button.
  showLabel?: boolean;
}

type CopyState = 'idle' | 'copied' | 'failed';

const RESET_MS = 2000;

export function CopyButton({ text, label, size = 'sm', showLabel = false }: CopyButtonProps) {
  const [state, setState] = useState<CopyState>('idle');

  useEffect(() => {
    if (state === 'idle') {
      return;
    }
    const timer = window.setTimeout(() => setState('idle'), RESET_MS);
    return () => window.clearTimeout(timer);
  }, [state]);

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('failed');
    }
  }

  const icon = state === 'copied' ? Check : Copy;
  return (
    <>
      {showLabel ? (
        <Button size={size} icon={icon} onClick={() => void copy()}>
          {label}
        </Button>
      ) : (
        <IconButton label={label} icon={icon} size={size} onClick={() => void copy()} />
      )}
      <span className="visually-hidden" aria-live="polite">
        {state === 'copied' ? messages.copy.copied : state === 'failed' ? messages.copy.failed : ''}
      </span>
    </>
  );
}
