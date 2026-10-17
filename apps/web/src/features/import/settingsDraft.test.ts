import { describe, expect, test } from 'vitest';
import { messages } from '../../messages';
import { settings } from '../../test/fixtures';
import { fieldErrors, isDirty, toDraft, toRequest } from './settingsDraft';

describe('settingsDraft', () => {
  test('round-trips the saved settings, with limits in KB and MB', () => {
    const saved = settings({
      excludePatterns: ['docs/generated/', '*.min.js'],
      disabledDefaultRules: ['vendor'],
      maxFileBytes: 2048,
      maxProjectBytes: 10 * 1024 * 1024,
    });

    const draft = toDraft(saved);

    expect(draft).toEqual({
      patterns: 'docs/generated/\n*.min.js',
      disabledRules: ['vendor'],
      maxFileKb: '2',
      maxProjectMb: '10',
    });
    expect(toRequest(draft)).toEqual({
      excludePatterns: ['docs/generated/', '*.min.js'],
      disabledDefaultRules: ['vendor'],
      maxFileBytes: 2048,
      maxProjectBytes: 10 * 1024 * 1024,
    });
    expect(isDirty(draft, saved)).toBe(false);
  });

  test('ignores blank lines and surrounding spaces, and empty limits fall back to the default', () => {
    const draft = {
      patterns: '  build/ \n\n dist/',
      disabledRules: [],
      maxFileKb: ' ',
      maxProjectMb: '',
    };

    expect(toRequest(draft)).toEqual({
      excludePatterns: ['build/', 'dist/'],
      disabledDefaultRules: [],
      maxFileBytes: null,
      maxProjectBytes: null,
    });
    expect(isDirty({ ...draft, patterns: '\n' }, settings())).toBe(false);
    expect(isDirty(draft, settings())).toBe(true);
  });

  test('maps indexed field errors to the pattern they refer to', () => {
    const request = toRequest({
      patterns: 'ok/\n../escape',
      disabledRules: [],
      maxFileKb: '',
      maxProjectMb: '',
    });

    const errors = fieldErrors(
      [
        { field: 'excludePatterns[1]', message: 'Padrão inválido.' },
        { field: 'maxFileBytes', message: 'Fora da faixa.' },
        { field: 'unknown', message: 'Ignorado.' },
      ],
      request,
    );

    expect(errors).toEqual({
      excludePatterns: [messages.settings.patternError('../escape', 'Padrão inválido.')],
      maxFileBytes: ['Fora da faixa.'],
    });
  });
});
