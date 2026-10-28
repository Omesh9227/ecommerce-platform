import { Link } from 'react-router-dom';

export function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2 rounded text-white" aria-label="Cartwheel home">
      <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden>
        <circle cx="16" cy="16" r="13" fill="none" stroke="#F2A900" strokeWidth="3" />
        <circle cx="16" cy="16" r="3" fill="#F2A900" />
        {[0, 60, 120].map((deg) => (
          <line key={deg} x1="16" y1="5" x2="16" y2="27" stroke="#F2A900" strokeWidth="1.6" transform={`rotate(${deg} 16 16)`} />
        ))}
      </svg>
      <span className="font-display text-2xl font-extrabold tracking-tight">cartwheel</span>
    </Link>
  );
}
