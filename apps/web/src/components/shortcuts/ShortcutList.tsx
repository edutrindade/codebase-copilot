import { Fragment } from 'react';
import { isApplePlatform, useSingleKeyShortcuts } from '../../app/shortcuts';
import { messages } from '../../messages';
import { cx } from '../ui/cx';
import { Kbd } from '../ui/Kbd';
import './ShortcutList.css';

interface ShortcutRow {
  label: string;
  // Keys pressed together, in each step; the steps are alternatives or a sequence.
  steps: string[][];
  join?: 'or' | 'then';
  singleKey: boolean;
}

function rows(): ShortcutRow[] {
  const text = messages.shortcuts.actions;
  const mod = isApplePlatform() ? '⌘' : 'Ctrl';
  return [
    { label: text.openPalette, steps: [[mod, 'K']], singleKey: false },
    { label: text.showShortcuts, steps: [['?']], singleKey: true },
    { label: text.close, steps: [['Esc']], singleKey: false },
    { label: text.goOverview, steps: [['g'], ['o']], join: 'then', singleKey: true },
    { label: text.goAsk, steps: [['g'], ['p']], join: 'then', singleKey: true },
    { label: text.goFiles, steps: [['g'], ['a']], join: 'then', singleKey: true },
    { label: text.goHistory, steps: [['g'], ['h']], join: 'then', singleKey: true },
    { label: text.focusQuestion, steps: [['/']], singleKey: true },
    { label: text.send, steps: [[mod, 'Enter']], singleKey: false },
    { label: text.evidence, steps: [['['], [']']], join: 'or', singleKey: true },
  ];
}

export function ShortcutList() {
  const singleKeyEnabled = useSingleKeyShortcuts();
  const text = messages.shortcuts;
  return (
    <dl className="shortcut-list">
      {rows().map((row) => {
        const off = row.singleKey && !singleKeyEnabled;
        return (
          <div
            key={row.label}
            className={cx('shortcut-list__row', off && 'shortcut-list__row--off')}
          >
            <dt className="shortcut-list__keys">
              {row.steps.map((step, index) => (
                <Fragment key={step.join('+')}>
                  {index > 0 && (
                    <span className="shortcut-list__join">
                      {row.join === 'then' ? text.then : text.or}
                    </span>
                  )}
                  {step.map((key) => (
                    <Kbd key={key}>{key}</Kbd>
                  ))}
                </Fragment>
              ))}
            </dt>
            <dd>
              {row.label}
              {off && <span className="muted"> ({text.singleKeyOff})</span>}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}
