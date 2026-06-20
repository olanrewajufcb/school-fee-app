import React from 'react';
import { useStore } from '@/store/useStore';
import { getServers } from '@/utils/openapiParser';

interface SettingsDropdownProps {
  onClose: () => void;
}

const SettingsDropdown: React.FC<SettingsDropdownProps> = ({ onClose }) => {
  const { serverUrl, setServerUrl, authToken, setAuthToken } = useStore();
  const servers = getServers();

  return (
    <div className="absolute right-0 top-full mt-1 w-72 bg-slate-800 border border-slate-700 rounded-lg shadow-xl z-50 p-4">
      {/* Server URL */}
      <div className="mb-4">
        <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-2">
          Server URL
        </label>
        <div className="space-y-1.5">
          {servers.map((s) => (
            <label key={s.url} className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="serverUrl"
                value={s.url}
                checked={serverUrl === s.url}
                onChange={(e) => setServerUrl(e.target.value)}
                className="accent-blue-500"
              />
              <span className="text-sm text-slate-300">
                {s.description} <span className="text-slate-500 font-mono text-xs">({s.url})</span>
              </span>
            </label>
          ))}
        </div>
      </div>

      {/* Auth Token */}
      <div className="mb-4">
        <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-2">
          Bearer Token
        </label>
        <textarea
          value={authToken}
          onChange={(e) => setAuthToken(e.target.value)}
          placeholder="Paste your Bearer token..."
          className="w-full h-16 px-3 py-2 bg-slate-900 border border-slate-700 rounded-md text-xs font-mono text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500 resize-none"
        />
      </div>

      {/* Close button */}
      <button
        onClick={onClose}
        className="w-full py-2 bg-slate-700 hover:bg-slate-600 text-slate-200 text-sm rounded-md transition-colors"
      >
        Done
      </button>
    </div>
  );
};

export default SettingsDropdown;
