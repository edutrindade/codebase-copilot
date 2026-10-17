import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
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

test('falls back to a generic message for anything else', () => {
  render(<ProblemAlert error={new TypeError('boom')} />);

  expect(screen.getByRole('alert')).toHaveTextContent(messages.errors.unexpectedTitle);
  expect(screen.getByRole('alert')).not.toHaveTextContent('boom');
});
