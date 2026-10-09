import type { FC } from 'react';
import { useTheme } from '../lib/theme';

export const ThemeToggle: FC<{ size?: number; className?: string }> = ({ size = 20, className = '' }) => {
  const { isDark, toggleTheme } = useTheme();

  return (
    <button
      type="button"
      className={`iconbtn ${className}`}
      onClick={toggleTheme}
      title={isDark ? 'Beralih ke Tema Terang (Light Mode)' : 'Beralih ke Tema Gelap (Dark Mode)'}
      aria-label="Ubah Tema Gelap/Terang"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'pointer',
        transition: 'transform 0.2s ease, color 0.2s ease',
      }}
    >
      {isDark ? (
        // Ikon Matahari (Sun) untuk beralih ke Light
        <svg
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill="none"
          stroke="#FBBF24"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="12" cy="12" r="5" fill="#FBBF24" fillOpacity="0.2" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
      ) : (
        // Ikon Bulan (Moon) untuk beralih ke Dark
        <svg
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill="none"
          stroke="var(--text-2)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      )}
    </button>
  );
};
