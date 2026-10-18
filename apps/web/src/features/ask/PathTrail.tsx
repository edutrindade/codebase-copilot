import { Fragment } from 'react';

// The path as a trail, with the file name stressed; the text content stays the full path.
export function PathTrail({ path }: { path: string }) {
  const parts = path.split('/');
  return (
    <code className="path-trail">
      {parts.map((part, index) => {
        const last = index === parts.length - 1;
        return (
          <Fragment key={index}>
            {last ? <span className="path-trail__name">{part}</span> : part}
            {!last && <span className="path-trail__separator">/</span>}
          </Fragment>
        );
      })}
    </code>
  );
}
