import type { ReactNode } from 'react';
import './illustrations.css';

// Decorative, drawn with the theme tokens so they follow light and dark.
function Illustration({ children }: { children: ReactNode }) {
  return (
    <svg className="illustration" viewBox="0 0 120 80" aria-hidden="true" focusable="false">
      {children}
    </svg>
  );
}

export function WorkspaceIllustration() {
  return (
    <Illustration>
      <rect className="illustration__surface" x="20" y="10" width="36" height="27" rx="6" />
      <rect className="illustration__surface" x="64" y="10" width="36" height="27" rx="6" />
      <rect className="illustration__surface" x="20" y="44" width="36" height="27" rx="6" />
      <rect
        className="illustration__soft illustration__dashed"
        x="64"
        y="44"
        width="36"
        height="27"
        rx="6"
      />
      <path
        className="illustration__line"
        d="M28 20h14M28 27h20M72 20h18M72 27h12M28 54h10M28 61h20"
      />
      <path className="illustration__accent-line" d="M82 51.5v12M76 57.5h12" />
    </Illustration>
  );
}

export function ImportIllustration() {
  return (
    <Illustration>
      <path
        className="illustration__surface"
        d="M18 18a4 4 0 0 1 4-4h20l6 6h46a4 4 0 0 1 4 4v40a4 4 0 0 1-4 4H22a4 4 0 0 1-4-4z"
      />
      <path className="illustration__line" d="M18 30h80M30 42h22M30 50h34M30 58h16" />
      <circle className="illustration__accent" cx="90" cy="60" r="13" />
      <path className="illustration__on-accent" d="M90 53v13M84.5 60.5l5.5 5.5 5.5-5.5" />
    </Illustration>
  );
}

export function AskIllustration() {
  return (
    <Illustration>
      <rect className="illustration__surface" x="12" y="8" width="70" height="46" rx="8" />
      <rect className="illustration__evidence" x="19" y="25" width="56" height="9" rx="2" />
      <path className="illustration__line" d="M23 19h34M23 29.5h44M23 41h26" />
      <rect className="illustration__soft" x="48" y="44" width="60" height="28" rx="8" />
      <path className="illustration__accent-line" d="M64 51h-4v14h4M92 51h4v14h-4" />
      <circle className="illustration__accent" cx="78" cy="58" r="3.5" />
    </Illustration>
  );
}

export function EmptyProjectsIllustration() {
  return (
    <Illustration>
      <path
        className="illustration__soft illustration__dashed"
        d="M22 18a4 4 0 0 1 4-4h20l6 6h42a4 4 0 0 1 4 4v40a4 4 0 0 1-4 4H26a4 4 0 0 1-4-4z"
      />
      <path className="illustration__line" d="M22 30h76" />
      <circle className="illustration__accent" cx="60" cy="48" r="11" />
      <path className="illustration__on-accent" d="M60 42v12M54 48h12" />
    </Illustration>
  );
}
