import { Link, useLocation } from 'react-router';
import {
  LayoutDashboard,
  School,
  Receipt,
  CreditCard,
  Bell,
  LogOut,
  GraduationCap,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { useState } from 'react';

interface SidebarProps {
  username: string | null;
  onLogout: () => void;
}

const menuItems = [
  { path: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/schools', icon: School, label: 'Schools' },
  { path: '/fees', icon: Receipt, label: 'Fee Management' },
  { path: '/payments', icon: CreditCard, label: 'Payments' },
  { path: '/notifications', icon: Bell, label: 'Notifications' },
];

export function Sidebar({ username, onLogout }: SidebarProps) {
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={`bg-slate-900 text-white transition-all duration-300 flex flex-col ${
        collapsed ? 'w-16' : 'w-64'
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-slate-700">
        {!collapsed && (
          <div className="flex items-center gap-2">
            <GraduationCap className="w-6 h-6 text-blue-400" />
            <span className="font-bold text-lg">SchoolFee</span>
          </div>
        )}
        {collapsed && <GraduationCap className="w-6 h-6 text-blue-400 mx-auto" />}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="p-1 rounded hover:bg-slate-700 transition-colors"
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4">
        {menuItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.path;
          return (
            <Link
              key={item.path}
              to={item.path}
              className={`flex items-center gap-3 px-4 py-3 transition-colors ${
                isActive
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
              } ${collapsed ? 'justify-center' : ''}`}
              title={collapsed ? item.label : undefined}
            >
              <Icon className="w-5 h-5 flex-shrink-0" />
              {!collapsed && <span className="text-sm font-medium">{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="border-t border-slate-700 p-4">
        {!collapsed ? (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-sm font-bold">
                {username?.charAt(0).toUpperCase() || 'U'}
              </div>
              <div className="text-sm">
                <p className="font-medium truncate max-w-[120px]">{username || 'User'}</p>
                <p className="text-slate-400 text-xs">Admin</p>
              </div>
            </div>
            <button
              onClick={onLogout}
              className="p-2 rounded hover:bg-slate-700 transition-colors text-slate-400 hover:text-red-400"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <button
            onClick={onLogout}
            className="p-2 rounded hover:bg-slate-700 transition-colors text-slate-400 hover:text-red-400 mx-auto block"
            title="Logout"
          >
            <LogOut className="w-5 h-5" />
          </button>
        )}
      </div>
    </aside>
  );
}
