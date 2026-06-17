import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  Terminal,
  Search,
  X,
  Moon,
  Sun,
  Wrench,
  Settings,
  Menu,
} from 'lucide-react';
import { useStore } from '@/store/useStore';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import SettingsDropdown from './SettingsDropdown';

const TopBar: React.FC = () => {
  const {
    toggleSidebar,
    tryItOpen,
    toggleTryIt,
    theme,
    setTheme,
    searchQuery,
    setSearchQuery,
    queue,
  } = useStore();

  const isOnline = useOnlineStatus();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const settingsRef = useRef<HTMLDivElement>(null);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
      e.preventDefault();
      searchRef.current?.focus();
    }
    if ((e.metaKey || e.ctrlKey) && e.key === 'b') {
      e.preventDefault();
      toggleSidebar();
    }
    if ((e.metaKey || e.ctrlKey) && e.key === 't') {
      e.preventDefault();
      toggleTryIt();
    }
  }, [toggleSidebar, toggleTryIt]);

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) {
        setSettingsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <header className="fixed top-0 left-0 right-0 h-12 bg-slate-900 border-b border-slate-700 flex items-center justify-between px-4 z-50">
      {/* Left: Logo & Title */}
      <div className="flex items-center gap-3">
        <button
          onClick={toggleSidebar}
          className="p-1.5 rounded hover:bg-slate-800 transition-colors lg:hidden"
          title="Toggle sidebar (Ctrl+B)"
        >
          <Menu size={18} className="text-slate-300" />
        </button>
        <div className="flex items-center gap-2">
          <Terminal size={16} className="text-blue-500" />
          <div className="w-px h-5 bg-slate-700" />
          <h1 className="text-sm font-semibold text-slate-100 hidden sm:block">School Fee API</h1>
          <span className="text-[11px] font-mono text-blue-400 bg-blue-500/10 px-1.5 py-0.5 rounded">v1.0.0</span>
        </div>
      </div>

      {/* Center: Search */}
      <div className="flex-1 max-w-md mx-4">
        <div className="relative">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            ref={searchRef}
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search endpoints (Ctrl+K)..."
            className="w-full h-8 pl-9 pr-8 bg-slate-800 border border-slate-700 rounded-md text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
          />
          {searchQuery && (
            <button
              onClick={() => { setSearchQuery(''); searchRef.current?.focus(); }}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded hover:bg-slate-700"
            >
              <X size={14} className="text-slate-400" />
            </button>
          )}
        </div>
      </div>

      {/* Right: Actions */}
      <div className="flex items-center gap-1">
        {/* Online Status */}
        {!isOnline && (
          <div className="flex items-center gap-1.5 px-2 py-1 bg-amber-500/15 border border-amber-500/30 rounded-md mr-2">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
            <span className="text-xs text-amber-400 font-medium hidden sm:block">Offline</span>
          </div>
        )}

        {/* Theme Toggle */}
        <button
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          className="p-2 rounded hover:bg-slate-800 transition-colors"
          title="Toggle theme"
        >
          {theme === 'dark' ? (
            <Sun size={18} className="text-slate-400" />
          ) : (
            <Moon size={18} className="text-slate-400" />
          )}
        </button>

        {/* Try It Toggle */}
        <button
          onClick={toggleTryIt}
          className={`relative p-2 rounded transition-colors ${tryItOpen ? 'bg-blue-500/15 text-blue-400' : 'hover:bg-slate-800 text-slate-400'}`}
          title="Toggle Try It panel (Ctrl+T)"
        >
          <Wrench size={18} />
          {queue.length > 0 && (
            <span className="absolute -top-1 -right-1 bg-amber-500 text-slate-900 text-[10px] font-bold px-1.5 py-0.5 rounded-full">
              {queue.length}
            </span>
          )}
        </button>

        {/* Settings */}
        <div className="relative" ref={settingsRef}>
          <button
            onClick={() => setSettingsOpen(!settingsOpen)}
            className="p-2 rounded hover:bg-slate-800 transition-colors text-slate-400"
            title="Settings"
          >
            <Settings size={18} />
          </button>
          {settingsOpen && <SettingsDropdown onClose={() => setSettingsOpen(false)} />}
        </div>
      </div>
    </header>
  );
};

export default TopBar;
