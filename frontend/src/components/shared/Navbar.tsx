import { useEffect, useRef, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../store/authStore';

export function Navbar() {
  const { name, email, isAuthenticated, logout } = useAuthStore();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleOutsideClick(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  const handleLogout = () => {
    logout();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  const displayName = name || email || 'User';
  const avatarLetter = displayName.charAt(0).toUpperCase() || 'U';

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
      isActive
        ? 'bg-slate-700/70 text-slate-100'
        : 'text-slate-300 hover:text-slate-100 hover:bg-slate-700/40'
    }`;

  return (
    <nav className="sticky top-0 z-50 border-b border-white/10 bg-slate-900/80 backdrop-blur-xl">
      <div className="mx-auto flex max-w-6xl items-center px-4 py-3 sm:px-6 lg:px-8">
        {/* Brand */}
        <span className="mr-6 text-base font-bold tracking-tight text-slate-100">
          Sprint Planner
        </span>

        {/* Desktop nav links */}
        <div className="hidden items-center gap-1 md:flex">
          <NavLink to="/dashboard" className={navLinkClass}>
            Dashboard
          </NavLink>
          <NavLink to="/teams" className={navLinkClass}>
            Teams
          </NavLink>
        </div>

        {/* Spacer */}
        <div className="flex-1" />

        {/* User menu */}
        {isAuthenticated && (
          <div className="relative" ref={dropdownRef}>
            <button
              type="button"
              onClick={() => setDropdownOpen((prev) => !prev)}
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-slate-200 transition-colors hover:bg-slate-700/40"
            >
              <span
                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                style={{
                  background: 'var(--color-background-info)',
                  color: 'var(--color-text-info)',
                }}
              >
                {avatarLetter}
              </span>
              <span className="hidden font-medium sm:inline">{displayName}</span>
              <span className="text-xs text-slate-400">▾</span>
            </button>

            {dropdownOpen && (
              <div className="absolute right-0 top-full z-10 mt-1 w-48 rounded-xl border border-white/10 bg-slate-900/95 py-1 shadow-card backdrop-blur-xl">
                <button
                  type="button"
                  onClick={() => {
                    navigate('/profile');
                    setDropdownOpen(false);
                  }}
                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-slate-200 transition-colors hover:bg-slate-700/50"
                >
                  <span>👤</span>
                  <span>Profile</span>
                </button>
                <button
                  type="button"
                  disabled
                  className="flex w-full cursor-not-allowed items-center justify-between px-4 py-2 text-sm text-slate-500"
                >
                  <span className="flex items-center gap-2">
                    <span>⚙️</span>
                    <span>Settings</span>
                  </span>
                  <span className="rounded-full bg-slate-700/60 px-2 py-0.5 text-xs text-slate-400">
                    Soon
                  </span>
                </button>
                <div className="my-1 border-t border-white/10" />
                <button
                  type="button"
                  onClick={handleLogout}
                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-400 transition-colors hover:bg-red-500/10"
                >
                  <span>🚪</span>
                  <span>Logout</span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* Hamburger — mobile only */}
        <button
          type="button"
          onClick={() => setMobileMenuOpen((prev) => !prev)}
          className="ml-2 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-300 transition-colors hover:bg-slate-700/40 md:hidden"
          aria-label="Toggle navigation"
        >
          {mobileMenuOpen ? '✕' : '☰'}
        </button>
      </div>

      {/* Mobile nav drawer */}
      {mobileMenuOpen && (
        <div className="border-t border-white/10 md:hidden">
          <div className="flex flex-col gap-1 px-4 py-3">
            <NavLink
              to="/dashboard"
              className={navLinkClass}
              onClick={() => setMobileMenuOpen(false)}
            >
              Dashboard
            </NavLink>
            <NavLink
              to="/teams"
              className={navLinkClass}
              onClick={() => setMobileMenuOpen(false)}
            >
              Teams
            </NavLink>
          </div>
        </div>
      )}
    </nav>
  );
}
