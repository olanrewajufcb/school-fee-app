import { Outlet } from 'react-router';
import { Sidebar } from './Sidebar';
import { Toaster } from '@/components/ui/sonner';

interface LayoutProps {
  username: string | null;
  onLogout: () => void;
}

export function Layout({ username, onLogout }: LayoutProps) {
  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar username={username} onLogout={onLogout} />
      <main className="flex-1 overflow-auto">
        <div className="p-6">
          <Outlet />
        </div>
      </main>
      <Toaster position="top-right" />
    </div>
  );
}
