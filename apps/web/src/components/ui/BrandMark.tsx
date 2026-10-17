import './BrandMark.css';

// Brackets around a dot: code, and the evidence inside it.
export function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
      <path
        className="brand-mark__frame"
        d="M11 5H6v22h5M21 5h5v22h-5"
        fill="none"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle className="brand-mark__dot" cx="16" cy="16" r="3.5" />
    </svg>
  );
}
