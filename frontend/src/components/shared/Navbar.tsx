import { useEffect, useRef, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../store/authStore';
import { getTrelloStatus } from '../../api/profileApi';

export function Navbar() {
  const { name, email, isAuthenticated, logout } = useAuthStore();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const trelloStatusQuery = useQuery({
    queryKey: ['trello-status'],
    queryFn: getTrelloStatus,
    staleTime: 1000 * 60 * 30,
    enabled: isAuthenticated,
    retry: false,
  });

  const isTrelloConnected = trelloStatusQuery.data?.connected ?? false;

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
        <div className="hidden items-center gap-1 sm:flex">
          <NavLink to="/dashboard" className={navLinkClass}>
            Dashboard
          </NavLink>
          <NavLink to="/teams" className={navLinkClass}>
            Teams
          </NavLink>
          <NavLink
            to="/faq"
            className={({ isActive }) =>
              `px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                isActive
                  ? 'bg-slate-700/70 text-slate-300'
                  : 'text-slate-500 hover:text-slate-300 hover:bg-slate-700/40'
              }`
            }
          >
            FAQ
          </NavLink>
        </div>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Trello connection indicator — desktop only */}
        {isAuthenticated && !isTrelloConnected && (
          <NavLink
            to="/profile#trello"
            className="mr-3 hidden items-center gap-1 text-xs text-slate-500 hover:text-slate-300 transition-colors sm:flex"
          >
            🔗 Connect Trello
          </NavLink>
        )}

        {/* User menu */}
        {isAuthenticated && (
          <div className="relative" ref={dropdownRef}>
            <button
              type="button"
              onClick={() => setDropdownOpen((prev) => !prev)}
              className="flex min-h-[44px] items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-slate-200 transition-colors hover:bg-slate-700/40"
            >
              <span className="relative">
                <span
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                  style={{
                    background: 'var(--color-background-info)',
                    color: 'var(--color-text-info)',
                  }}
                >
                  {avatarLetter}
                </span>
                {isTrelloConnected && (
                  <span className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full bg-emerald-400 ring-2 ring-slate-900" />
                )}
              </span>
              <span className="hidden font-medium sm:inline">{displayName}</span>
              <span className="text-xs text-slate-400">▾</span>
            </button>

            {dropdownOpen && (
              <div className="absolute right-0 top-full z-10 mt-1 w-48 max-w-[calc(100vw-2rem)] rounded-xl border border-white/10 bg-slate-900/95 py-1 shadow-card backdrop-blur-xl">
                <button
                  type="button"
                  onClick={() => {
                    navigate('/profile');
                    setDropdownOpen(false);
                  }}
                  className="flex min-h-[44px] w-full items-center gap-2 px-4 py-2 text-sm text-slate-200 transition-colors hover:bg-slate-700/50"
                >
                  <span>👤</span>
                  <span>Profile</span>
                </button>
                <button
                  type="button"
                  disabled
                  className="flex min-h-[44px] w-full cursor-not-allowed items-center justify-between px-4 py-2 text-sm text-slate-500"
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
                  className="flex min-h-[44px] w-full items-center gap-2 px-4 py-2 text-sm text-red-400 transition-colors hover:bg-red-500/10"
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
          className="ml-2 flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-slate-300 transition-colors hover:bg-slate-700/40 sm:hidden"
          aria-label="Toggle navigation"
        >
          {mobileMenuOpen ? '✕' : '☰'}
        </button>
      </div>

      {/* Mobile nav drawer */}
      {mobileMenuOpen && (
        <div className="border-t border-white/10 sm:hidden">
          <div className="flex flex-col gap-1 px-4 py-3">
            <NavLink
              to="/dashboard"
              className={({ isActive }) =>
                `flex min-h-[44px] items-center px-3 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-slate-700/70 text-slate-100'
                    : 'text-slate-300 hover:text-slate-100 hover:bg-slate-700/40'
                }`
              }
              onClick={() => setMobileMenuOpen(false)}
            >
              Dashboard
            </NavLink>
            <NavLink
              to="/teams"
              className={({ isActive }) =>
                `flex min-h-[44px] items-center px-3 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-slate-700/70 text-slate-100'
                    : 'text-slate-300 hover:text-slate-100 hover:bg-slate-700/40'
                }`
              }
              onClick={() => setMobileMenuOpen(false)}
            >
              Teams
            </NavLink>
            <NavLink
              to="/faq"
              className={({ isActive }) =>
                `flex min-h-[44px] items-center px-3 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-slate-700/70 text-slate-300'
                    : 'text-slate-500 hover:text-slate-300 hover:bg-slate-700/40'
                }`
              }
              onClick={() => setMobileMenuOpen(false)}
            >
              FAQ
            </NavLink>
            {isAuthenticated && !isTrelloConnected && (
              <NavLink
                to="/profile#trello"
                className="flex min-h-[44px] items-center px-3 text-xs text-slate-500"
                onClick={() => setMobileMenuOpen(false)}
              >
                🔗 Connect Trello
              </NavLink>
            )}
          </div>
        </div>
      )}
    </nav>
  );
}
