import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, type FormEvent } from 'react';
import { ApiError } from '../../api/errors';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { formatBytes } from '../../format';
import { messages } from '../../messages';
import { importKeys, updateSettings, type Settings } from './api';
import { fieldErrors, isDirty, toRequest, type SettingsDraft } from './settingsDraft';

export const SETTINGS_SECTION_ID = 'exclusions';

interface SettingsFormProps {
  projectId: string;
  settings: Settings;
  draft: SettingsDraft;
  onDraftChange: (draft: SettingsDraft) => void;
  onDiscard: () => void;
  onSaved: () => void;
}

export function SettingsForm({
  projectId,
  settings,
  draft,
  onDraftChange,
  onDiscard,
  onSaved,
}: SettingsFormProps) {
  const ids = {
    patterns: useId(),
    patternsHint: useId(),
    patternsErrors: useId(),
    maxFile: useId(),
    maxFileHint: useId(),
    maxProject: useId(),
    maxFileErrors: useId(),
    maxProjectHint: useId(),
    maxProjectErrors: useId(),
    rulesErrors: useId(),
  };
  const queryClient = useQueryClient();
  const request = toRequest(draft);
  const mutation = useMutation({
    mutationFn: () => updateSettings(projectId, request),
    onSuccess: async (saved) => {
      queryClient.setQueryData(importKeys.settings(projectId), saved);
      await queryClient.invalidateQueries({ queryKey: importKeys.inventory(projectId) });
      onSaved();
    },
  });
  const problem = mutation.error instanceof ApiError ? mutation.error.problem : null;
  const errors = fieldErrors(problem?.errors ?? [], request);
  const dirty = isDirty(draft, settings);

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  function toggleRule(ruleId: string, enabled: boolean) {
    const others = draft.disabledRules.filter((id) => id !== ruleId);
    onDraftChange({ ...draft, disabledRules: enabled ? others : [...others, ruleId] });
  }

  function discard() {
    mutation.reset();
    onDiscard();
  }

  return (
    <Card as="form" className="stack" onSubmit={submit} aria-labelledby={SETTINGS_SECTION_ID}>
      <h2 id={SETTINGS_SECTION_ID} tabIndex={-1}>
        {messages.settings.title}
      </h2>
      <p className="muted">{messages.settings.detail}</p>
      <fieldset className="rules" aria-describedby={ids.rulesErrors}>
        <legend>{messages.settings.rulesLegend}</legend>
        <ul className="rules__list">
          {settings.defaultRules.map((rule) => (
            <li key={rule.id}>
              <label className="rules__rule">
                <input
                  type="checkbox"
                  checked={!rule.overridable || !draft.disabledRules.includes(rule.id)}
                  disabled={!rule.overridable}
                  aria-label={messages.settings.ruleEnabled(rule.id)}
                  onChange={(event) => toggleRule(rule.id, event.target.checked)}
                />
                <span>
                  <strong>{rule.id}</strong>{' '}
                  <span className="muted">
                    ({messages.settings.categories[rule.category]}
                    {!rule.overridable && `, ${messages.settings.ruleRequired}`})
                  </span>
                  <br />
                  <code className="rules__patterns">{rule.patterns.join('  ')}</code>
                </span>
              </label>
            </li>
          ))}
        </ul>
        <FieldErrors id={ids.rulesErrors} messages={errors.disabledDefaultRules} />
      </fieldset>
      <div className="field field--wide">
        <label htmlFor={ids.patterns}>{messages.settings.patternsLabel}</label>
        <textarea
          id={ids.patterns}
          rows={5}
          spellCheck={false}
          value={draft.patterns}
          aria-invalid={errors.excludePatterns ? true : undefined}
          aria-describedby={`${ids.patternsHint} ${ids.patternsErrors}`}
          onChange={(event) => onDraftChange({ ...draft, patterns: event.target.value })}
        />
        <p id={ids.patternsHint} className="muted field__hint">
          {messages.settings.patternsHint}
        </p>
        <FieldErrors id={ids.patternsErrors} messages={errors.excludePatterns} />
      </div>
      <div className="field">
        <label htmlFor={ids.maxFile}>{messages.settings.maxFileLabel}</label>
        <input
          id={ids.maxFile}
          type="number"
          min={1}
          step="any"
          inputMode="decimal"
          value={draft.maxFileKb}
          aria-invalid={errors.maxFileBytes ? true : undefined}
          aria-describedby={`${ids.maxFileHint} ${ids.maxFileErrors}`}
          onChange={(event) => onDraftChange({ ...draft, maxFileKb: event.target.value })}
        />
        <p id={ids.maxFileHint} className="muted field__hint">
          {messages.settings.limitHint(formatBytes(settings.effectiveMaxFileBytes))}
        </p>
        <FieldErrors id={ids.maxFileErrors} messages={errors.maxFileBytes} />
      </div>
      <div className="field">
        <label htmlFor={ids.maxProject}>{messages.settings.maxProjectLabel}</label>
        <input
          id={ids.maxProject}
          type="number"
          min={1}
          step="any"
          inputMode="decimal"
          value={draft.maxProjectMb}
          aria-invalid={errors.maxProjectBytes ? true : undefined}
          aria-describedby={`${ids.maxProjectHint} ${ids.maxProjectErrors}`}
          onChange={(event) => onDraftChange({ ...draft, maxProjectMb: event.target.value })}
        />
        <p id={ids.maxProjectHint} className="muted field__hint">
          {messages.settings.limitHint(formatBytes(settings.effectiveMaxProjectBytes))}
        </p>
        <FieldErrors id={ids.maxProjectErrors} messages={errors.maxProjectBytes} />
      </div>
      {mutation.isError && <ProblemAlert error={mutation.error} showFieldErrors={false} />}
      <div className="actions">
        <Button type="submit" variant="primary" disabled={!dirty} pending={mutation.isPending}>
          {mutation.isPending ? messages.settings.saving : messages.settings.save}
        </Button>
        <Button disabled={!dirty || mutation.isPending} onClick={discard}>
          {messages.settings.discard}
        </Button>
      </div>
    </Card>
  );
}

function FieldErrors({ id, messages: lines }: { id: string; messages?: string[] }) {
  if (!lines || lines.length === 0) {
    return <div id={id} />;
  }
  return (
    <ul id={id} className="field-error">
      {lines.map((line) => (
        <li key={line}>{line}</li>
      ))}
    </ul>
  );
}
