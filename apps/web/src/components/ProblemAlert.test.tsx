import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { ApiError, type Problem } from '../api/errors';
import { messages } from '../messages';
import { axeViolations } from '../test/axe';
import { ProblemAlert } from './ProblemAlert';

const projectNotFound: Problem = {
  type: 'about:blank',
  title: 'Projeto não encontrado',
  status: 404,
  detail: 'O projeto informado não existe.',
  instance: '/api/v1/projects/1',
  code: 'PROJECT_NOT_FOUND',
  requestId: '00000000-0000-4000-8000-000000000002',
};

test('shows the title, detail and request id of a ProblemDetail', async () => {
  render(<ProblemAlert error={new ApiError(404, projectNotFound)} />);

  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent('Projeto não encontrado');
  expect(alert).toHaveTextContent('O projeto informado não existe.');
  expect(alert).toHaveTextContent('00000000-0000-4000-8000-000000000002');
  expect(await axeViolations()).toEqual([]);
});

test('explains a network failure without a request id', () => {
  render(<ProblemAlert error={new ApiError(0, null)} />);

  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent(messages.errors.networkTitle);
  expect(alert).not.toHaveTextContent(messages.errors.requestId);
});

test.each([502, 503, 504])(
  'explains a proxy %i without a ProblemDetail as an unavailable server',
  (status) => {
    render(<ProblemAlert error={new ApiError(status, null)} onRetry={vi.fn()} />);

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent(messages.errors.networkTitle);
    expect(alert).toHaveTextContent(messages.errors.networkDetail);
    expect(screen.getByRole('button', { name: messages.errors.retry })).toBeInTheDocument();
  },
);

test('keeps the generic message for a 500 without a ProblemDetail', () => {
  render(<ProblemAlert error={new ApiError(500, null)} />);

  expect(screen.getByRole('alert')).toHaveTextContent(messages.errors.unexpectedTitle);
});

test('falls back to a generic message for anything else', () => {
  render(<ProblemAlert error={new TypeError('boom')} />);

  expect(screen.getByRole('alert')).toHaveTextContent(messages.errors.unexpectedTitle);
  expect(screen.getByRole('alert')).not.toHaveTextContent('boom');
});

test('suggests the recovery for the code and offers no retry for it', () => {
  const onRetry = vi.fn();
  render(<ProblemAlert error={new ApiError(404, projectNotFound)} onRetry={onRetry} />);

  expect(screen.getByRole('alert')).toHaveTextContent(messages.recovery.goBack);
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

test('offers a retry for a retryable error when the screen supports it', async () => {
  const onRetry = vi.fn();
  render(<ProblemAlert error={new ApiError(0, null)} onRetry={onRetry} />);

  await userEvent.click(screen.getByRole('button', { name: messages.errors.retry }));

  expect(onRetry).toHaveBeenCalledOnce();
});

test('lists the field errors of a validation problem', () => {
  const invalid: Problem = {
    ...projectNotFound,
    status: 400,
    code: 'VALIDATION_FAILED',
    errors: [{ field: 'name', message: 'O nome é obrigatório.' }],
  };
  render(<ProblemAlert error={new ApiError(400, invalid)} />);

  expect(screen.getByRole('listitem')).toHaveTextContent('O nome é obrigatório.');
});
