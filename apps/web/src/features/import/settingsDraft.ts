import type { FieldError } from '../../api/errors';
import { messages } from '../../messages';
import type { Settings, SettingsUpdate } from './api';

const KB = 1024;
const MB = 1024 * 1024;

export interface SettingsDraft {
  patterns: string;
  disabledRules: string[];
  maxFileKb: string;
  maxProjectMb: string;
}

export type SettingsField =
  'excludePatterns' | 'disabledDefaultRules' | 'maxFileBytes' | 'maxProjectBytes';

function toUnit(bytes: number | null, unit: number): string {
  return bytes === null ? '' : String(bytes / unit);
}

function fromUnit(value: string, unit: number): number | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : Math.round(Number(trimmed) * unit);
}

export function toDraft(settings: Settings): SettingsDraft {
  return {
    patterns: settings.excludePatterns.join('\n'),
    disabledRules: settings.disabledDefaultRules,
    maxFileKb: toUnit(settings.maxFileBytes, KB),
    maxProjectMb: toUnit(settings.maxProjectBytes, MB),
  };
}

export function toRequest(draft: SettingsDraft): Required<SettingsUpdate> {
  return {
    excludePatterns: draft.patterns
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line !== ''),
    disabledDefaultRules: [...draft.disabledRules].sort(),
    maxFileBytes: fromUnit(draft.maxFileKb, KB),
    maxProjectBytes: fromUnit(draft.maxProjectMb, MB),
  };
}

export function isDirty(draft: SettingsDraft, settings: Settings): boolean {
  return JSON.stringify(toRequest(draft)) !== JSON.stringify(toRequest(toDraft(settings)));
}

// Errors come indexed by the request (excludePatterns[3]), so they are shown with the pattern they refer to.
export function fieldErrors(
  errors: FieldError[],
  request: Required<SettingsUpdate>,
): Partial<Record<SettingsField, string[]>> {
  const byField: Partial<Record<SettingsField, string[]>> = {};
  for (const error of errors) {
    const match = /^(\w+)(?:\[(\d+)\])?$/.exec(error.field);
    const field = match?.[1] as SettingsField | undefined;
    if (!field || !(field in request)) {
      continue;
    }
    const index = match?.[2] === undefined ? null : Number(match[2]);
    const pattern =
      field === 'excludePatterns' && index !== null ? request.excludePatterns?.[index] : undefined;
    const message =
      pattern === undefined
        ? error.message
        : messages.settings.patternError(pattern, error.message);
    (byField[field] ??= []).push(message);
  }
  return byField;
}
