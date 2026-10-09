import type { FC } from 'react';

interface AppIconProps {
  app: string;
  size?: number;
}

/**
 * Ikon Grafis Modul Modern bergaya Odoo (PRD §15 & Gambar Referensi User 1)
 * Menampilkan ikon multi-warna, 3D isometrik, dan bentuk geometris yang wow & elegan.
 */
export const OdooAppIcon: FC<AppIconProps> = ({ app, size = 68 }) => {
  const code = app.toUpperCase();

  switch (code) {
    // ---------------------------------------------------- SCM / INVENTORY (Kubus 3D Isometrik)
    case 'SCM':
    case 'INVENTORY':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="cubeTop" x1="16" y1="12" x2="48" y2="28" gradientUnits="userSpaceOnUse">
              <stop stopColor="#FBBF24" />
              <stop offset="1" stopColor="#F59E0B" />
            </linearGradient>
            <linearGradient id="cubeLeft" x1="16" y1="28" x2="32" y2="52" gradientUnits="userSpaceOnUse">
              <stop stopColor="#D97706" />
              <stop offset="1" stopColor="#B45309" />
            </linearGradient>
            <linearGradient id="cubeRight" x1="32" y1="28" x2="48" y2="52" gradientUnits="userSpaceOnUse">
              <stop stopColor="#831843" />
              <stop offset="1" stopColor="#6B21A8" />
            </linearGradient>
          </defs>
          {/* Top isometric face */}
          <path d="M32 10L50 20L32 30L14 20L32 10Z" fill="url(#cubeTop)" />
          {/* Left isometric face */}
          <path d="M14 20L32 30V52L14 42V20Z" fill="url(#cubeLeft)" />
          {/* Right isometric face */}
          <path d="M32 30L50 20V42L32 52V30Z" fill="url(#cubeRight)" />
          {/* Subtle inner box shadow detail */}
          <path d="M32 30L46 22.2M32 30V48" stroke="rgba(255,255,255,0.25)" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      );

    // ---------------------------------------------------- PRE / MANUFACTURING (Pabrik & Gear)
    case 'PRE':
    case 'MANUFACTURING':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="mfgRoof" x1="14" y1="24" x2="50" y2="50" gradientUnits="userSpaceOnUse">
              <stop stopColor="#0D9488" />
              <stop offset="1" stopColor="#047857" />
            </linearGradient>
            <linearGradient id="mfgBase" x1="14" y1="36" x2="50" y2="52" gradientUnits="userSpaceOnUse">
              <stop stopColor="#F59E0B" />
              <stop offset="1" stopColor="#D97706" />
            </linearGradient>
            <linearGradient id="mfgDrop" x1="26" y1="10" x2="38" y2="30" gradientUnits="userSpaceOnUse">
              <stop stopColor="#8B5CF6" />
              <stop offset="1" stopColor="#6D28D9" />
            </linearGradient>
          </defs>
          {/* Building chimney / roofs */}
          <path d="M14 50V34L26 38V30L38 34V22L50 26V50H14Z" fill="url(#mfgRoof)" />
          {/* Lower foundation blocks */}
          <rect x="14" y="42" width="36" height="8" rx="2" fill="url(#mfgBase)" />
          {/* Manufacturing liquid drop symbol (seperti tetesan di ikon Odoo Manufacturing) */}
          <path d="M32 10C32 10 24 20 24 25C24 29.4183 27.5817 33 32 33C36.4183 33 40 29.4183 40 25C40 20 32 10 32 10Z" fill="url(#mfgDrop)" />
          {/* Window / cut details */}
          <rect x="20" y="44" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.8" />
          <rect x="30" y="44" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.8" />
          <rect x="40" y="44" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.8" />
        </svg>
      );

    // ---------------------------------------------------- FIN / ACCOUNTING (Simbol % Odoo-style)
    case 'FIN':
    case 'ACCOUNTING':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="accSlash" x1="48" y1="14" x2="16" y2="50" gradientUnits="userSpaceOnUse">
              <stop stopColor="#D946EF" />
              <stop offset="1" stopColor="#9333EA" />
            </linearGradient>
            <linearGradient id="accCircle1" x1="16" y1="16" x2="28" y2="28" gradientUnits="userSpaceOnUse">
              <stop stopColor="#14B8A6" />
              <stop offset="1" stopColor="#0D9488" />
            </linearGradient>
            <linearGradient id="accCircle2" x1="36" y1="36" x2="48" y2="48" gradientUnits="userSpaceOnUse">
              <stop stopColor="#F59E0B" />
              <stop offset="1" stopColor="#D97706" />
            </linearGradient>
          </defs>
          {/* Diagonal bar */}
          <line x1="47" y1="15" x2="17" y2="49" stroke="url(#accSlash)" strokeWidth="8" strokeLinecap="round" />
          {/* Top circle */}
          <circle cx="22" cy="22" r="7.5" fill="url(#accCircle1)" />
          <circle cx="22" cy="22" r="3" fill="#FFFFFF" />
          {/* Bottom circle */}
          <circle cx="42" cy="42" r="7.5" fill="url(#accCircle2)" />
          <circle cx="42" cy="42" r="3" fill="#FFFFFF" />
        </svg>
      );

    // ---------------------------------------------------- PRC / PURCHASE (Dompet & Kartu Dua Tingkat Odoo)
    case 'PRC':
    case 'PURCHASE':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="prcTop" x1="14" y1="22" x2="50" y2="30" gradientUnits="userSpaceOnUse">
              <stop stopColor="#06B6D4" />
              <stop offset="1" stopColor="#0891B2" />
            </linearGradient>
            <linearGradient id="prcBottom" x1="14" y1="30" x2="50" y2="48" gradientUnits="userSpaceOnUse">
              <stop stopColor="#A855F7" />
              <stop offset="1" stopColor="#7E22CE" />
            </linearGradient>
          </defs>
          {/* Top teal band */}
          <rect x="14" y="20" width="36" height="12" rx="4" fill="url(#prcTop)" />
          {/* Lower purple card / wallet */}
          <rect x="14" y="28" width="36" height="20" rx="4" fill="url(#prcBottom)" />
          {/* Clasp / accent */}
          <circle cx="32" cy="38" r="3.5" fill="#FFFFFF" />
          <circle cx="32" cy="38" r="1.5" fill="#7E22CE" />
        </svg>
      );

    // ---------------------------------------------------- QMS / QUALITY (Lup & Persegi Odoo-style)
    case 'QMS':
    case 'QUALITY':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="qmsBox" x1="16" y1="16" x2="36" y2="36" gradientUnits="userSpaceOnUse">
              <stop stopColor="#38BDF8" />
              <stop offset="1" stopColor="#0284C7" />
            </linearGradient>
            <linearGradient id="qmsLens" x1="28" y1="28" x2="48" y2="48" gradientUnits="userSpaceOnUse">
              <stop stopColor="#EC4899" />
              <stop offset="1" stopColor="#BE185D" />
            </linearGradient>
          </defs>
          {/* Background square check item */}
          <rect x="16" y="16" width="22" height="22" rx="5" fill="url(#qmsBox)" />
          <path d="M22 27L26 31L33 23" stroke="#FFFFFF" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
          {/* Magnifying lens */}
          <circle cx="36" cy="36" r="10" stroke="url(#qmsLens)" strokeWidth="4.5" fill="rgba(255,255,255,0.85)" />
          <path d="M44 44L52 52" stroke="url(#qmsLens)" strokeWidth="5" strokeLinecap="round" />
        </svg>
      );

    // ---------------------------------------------------- RND / LITBANG (Lab Flask & Molecule)
    case 'RND':
    case 'RESEARCH':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="rndFlask" x1="24" y1="12" x2="40" y2="52" gradientUnits="userSpaceOnUse">
              <stop stopColor="#818CF8" />
              <stop offset="1" stopColor="#6366F1" />
            </linearGradient>
            <linearGradient id="rndFluid" x1="16" y1="36" x2="48" y2="52" gradientUnits="userSpaceOnUse">
              <stop stopColor="#EC4899" />
              <stop offset="1" stopColor="#A855F7" />
            </linearGradient>
          </defs>
          {/* Flask body outline */}
          <path d="M28 14H36M30 14V24L18 44C16.5 46.5 18 50 21 50H43C46 50 47.5 46.5 46 44L34 24V14" stroke="url(#rndFlask)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
          {/* Fluid fill inside */}
          <path d="M21.5 47C20.5 47 20 46 20.5 45L26 36C29 37 35 37 38 36L43.5 45C44 46 43.5 47 42.5 47H21.5Z" fill="url(#rndFluid)" />
          {/* Reaction bubbles */}
          <circle cx="32" cy="39" r="2" fill="#FFFFFF" />
          <circle cx="28" cy="43" r="1.5" fill="#FFFFFF" />
          <circle cx="36" cy="42" r="1.8" fill="#FFFFFF" />
        </svg>
      );

    // ---------------------------------------------------- HC / EMPLOYEES (Dua Orang Odoo-style)
    case 'HC':
    case 'EMPLOYEES':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="hcHead1" x1="20" y1="16" x2="30" y2="26" gradientUnits="userSpaceOnUse">
              <stop stopColor="#8B5CF6" />
              <stop offset="1" stopColor="#6D28D9" />
            </linearGradient>
            <linearGradient id="hcHead2" x1="34" y1="18" x2="44" y2="28" gradientUnits="userSpaceOnUse">
              <stop stopColor="#06B6D4" />
              <stop offset="1" stopColor="#0891B2" />
            </linearGradient>
            <linearGradient id="hcBody" x1="14" y1="34" x2="50" y2="50" gradientUnits="userSpaceOnUse">
              <stop stopColor="#F59E0B" />
              <stop offset="1" stopColor="#EF4444" />
            </linearGradient>
          </defs>
          {/* Person 1 (Purple) */}
          <circle cx="24" cy="22" r="6" fill="url(#hcHead1)" />
          {/* Person 2 (Cyan) */}
          <circle cx="40" cy="22" r="6" fill="url(#hcHead2)" />
          {/* United base/bodies */}
          <path d="M14 46C14 38 20 34 27 34C31 34 33 36 35 38C37 36 39 34 43 34C50 34 56 38 56 46H14Z" fill="url(#hcBody)" />
        </svg>
      );

    // ---------------------------------------------------- GA / GENERAL AFFAIRS (Gedung & Fasilitas)
    case 'GA':
    case 'FACILITIES':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="gaTower" x1="16" y1="14" x2="36" y2="50" gradientUnits="userSpaceOnUse">
              <stop stopColor="#0284C7" />
              <stop offset="1" stopColor="#0369A1" />
            </linearGradient>
            <linearGradient id="gaSide" x1="36" y1="26" x2="50" y2="50" gradientUnits="userSpaceOnUse">
              <stop stopColor="#F97316" />
              <stop offset="1" stopColor="#EA580C" />
            </linearGradient>
          </defs>
          <rect x="16" y="16" width="20" height="34" rx="3" fill="url(#gaTower)" />
          <rect x="36" y="28" width="14" height="22" rx="2" fill="url(#gaSide)" />
          {/* Tower windows */}
          <rect x="21" y="22" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="27" y="22" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="21" y="30" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="27" y="30" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="21" y="38" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="27" y="38" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="41" y="34" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
          <rect x="41" y="42" width="4" height="4" rx="1" fill="#FFFFFF" fillOpacity="0.85" />
        </svg>
      );

    // ---------------------------------------------------- ESS / SELF-SERVICE (To-do / Karyawan Odoo-style)
    case 'ESS':
    case 'TODO':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="essPencil" x1="20" y1="44" x2="44" y2="16" gradientUnits="userSpaceOnUse">
              <stop stopColor="#0284C7" />
              <stop offset="1" stopColor="#0EA5E9" />
            </linearGradient>
            <linearGradient id="essCheck" x1="20" y1="46" x2="46" y2="46" gradientUnits="userSpaceOnUse">
              <stop stopColor="#10B981" />
              <stop offset="1" stopColor="#059669" />
            </linearGradient>
          </defs>
          {/* Angled bold stylus / pencil */}
          <path d="M42 16L48 22L28 42L22 42L22 36L42 16Z" fill="url(#essPencil)" />
          {/* Bottom dynamic curve underline */}
          <path d="M18 48C28 44 40 44 48 48" stroke="url(#essCheck)" strokeWidth="4.5" strokeLinecap="round" />
        </svg>
      );

    // ---------------------------------------------------- SYS / SETTINGS (Hexagon Gear Odoo)
    case 'SYS':
    case 'SETTINGS':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="sysRing" x1="16" y1="16" x2="48" y2="48" gradientUnits="userSpaceOnUse">
              <stop stopColor="#F59E0B" />
              <stop offset="1" stopColor="#EA580C" />
            </linearGradient>
            <linearGradient id="sysCore" x1="24" y1="24" x2="40" y2="40" gradientUnits="userSpaceOnUse">
              <stop stopColor="#4F46E5" />
              <stop offset="1" stopColor="#7C3AED" />
            </linearGradient>
          </defs>
          {/* Hexagonal cog body */}
          <path d="M32 10L46 18V34L32 42L18 34V18L32 10Z" stroke="url(#sysRing)" strokeWidth="6" strokeLinejoin="round" />
          {/* Inner purple circle */}
          <circle cx="32" cy="26" r="6" fill="url(#sysCore)" />
          <circle cx="32" cy="26" r="2.5" fill="#FFFFFF" />
        </svg>
      );

    // ---------------------------------------------------- DASHBOARDS (4 Kotak Warna-warni Odoo)
    case 'DASH':
    case 'DASHBOARDS':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="16" y="16" width="13" height="13" rx="3.5" fill="#EC4899" />
          <rect x="35" y="16" width="13" height="13" rx="3.5" fill="#F59E0B" />
          <rect x="16" y="35" width="13" height="13" rx="3.5" fill="#06B6D4" />
          <rect x="35" y="35" width="13" height="13" rx="3.5" fill="#8B5CF6" />
        </svg>
      );

    // ---------------------------------------------------- APPROVAL / SIGN (Tanda Tangan Odoo)
    case 'APPROVAL':
    case 'SIGN':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="signGrad" x1="16" y1="24" x2="48" y2="44" gradientUnits="userSpaceOnUse">
              <stop stopColor="#0284C7" />
              <stop offset="1" stopColor="#0D9488" />
            </linearGradient>
          </defs>
          <path d="M18 38C22 30 26 22 28 32C30 42 36 26 40 34C43 40 48 30 50 32" stroke="url(#signGrad)" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="21" cy="45" r="2.5" fill="#0D9488" />
        </svg>
      );

    // ---------------------------------------------------- USERS / MANAGE USERS
    case 'USERS':
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="usersGrad1" x1="18" y1="18" x2="34" y2="34" gradientUnits="userSpaceOnUse">
              <stop stopColor="#3B82F6" />
              <stop offset="1" stopColor="#1D4ED8" />
            </linearGradient>
            <linearGradient id="usersGrad2" x1="32" y1="24" x2="48" y2="40" gradientUnits="userSpaceOnUse">
              <stop stopColor="#10B981" />
              <stop offset="1" stopColor="#047857" />
            </linearGradient>
          </defs>
          <circle cx="26" cy="22" r="7" fill="url(#usersGrad1)" />
          <path d="M14 44C14 37 19 33 26 33C33 33 38 37 38 44H14Z" fill="url(#usersGrad1)" />
          <circle cx="42" cy="26" r="5" fill="url(#usersGrad2)" />
          <path d="M34 46C34 41 38 38 43 38C48 38 52 41 52 46H34Z" fill="url(#usersGrad2)" />
        </svg>
      );

    // ---------------------------------------------------- DEFAULT / FALLBACK
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="16" y="16" width="32" height="32" rx="8" fill="#6366F1" />
          <text x="32" y="38" textAnchor="middle" fill="#FFFFFF" fontSize="18" fontWeight="bold" fontFamily="system-ui">
            {code.slice(0, 3)}
          </text>
        </svg>
      );
  }
};
