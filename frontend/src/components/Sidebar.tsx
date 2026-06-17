import React, { useCallback, useEffect } from 'react';
import {
  Shield,
  Users,
  Mail,
  Building2,
  GraduationCap,
  Layers,
  Wallet,
  CreditCard,
  Receipt,
  BarChart3,
  FileText,
  Bell,
  Webhook,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronRight as ChevronRightIcon,
  Circle,
} from 'lucide-react';
import type { ControllerGroup, Endpoint } from '@/types/openapi';
import { useStore } from '@/store/useStore';
import { useBreakpoint } from '@/hooks/useMediaQuery';
import MethodBadge from './MethodBadge';

const ICON_MAP: Record<string, React.FC<{ size?: number; className?: string }>> = {
  shield: Shield,
  users: Users,
  mail: Mail,
  'building-2': Building2,
  'graduation-cap': GraduationCap,
  layers: Layers,
  wallet: Wallet,
  'credit-card': CreditCard,
  receipt: Receipt,
  'bar-chart-3': BarChart3,
  'file-text': FileText,
  bell: Bell,
  webhook: Webhook,
  circle: Circle,
};

interface SidebarProps {
  controllers: ControllerGroup[];
}

const Sidebar: React.FC<SidebarProps> = ({ controllers }) => {
  const {
    sidebarOpen,
    toggleSidebar,
    activeEndpointId,
    setActiveEndpoint,
    setActiveController,
    expandedControllers,
    toggleController,
    searchQuery,
  } = useStore();

  const { isDesktop, isTablet } = useBreakpoint();

  // Determine sidebar width based on breakpoint and toggle state
  const showFullSidebar = sidebarOpen && (isDesktop || (isTablet && sidebarOpen));

  const handleEndpointClick = useCallback((ep: Endpoint) => {
    setActiveEndpoint(ep.id);
    setActiveController(ep.controller);

    // Scroll to the endpoint section
    const el = document.getElementById(`endpoint-${ep.id}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [setActiveEndpoint, setActiveController]);

  // Filter endpoints based on search query
  const filteredControllers = React.useMemo(() => {
    if (!searchQuery.trim()) return controllers;

    const q = searchQuery.toLowerCase();
    return controllers
      .map((ctrl) => ({
        ...ctrl,
        endpoints: ctrl.endpoints.filter(
          (ep) =>
            ep.path.toLowerCase().includes(q) ||
            ep.operation.operationId?.toLowerCase().includes(q) ||
            ep.operation.summary?.toLowerCase().includes(q) ||
            ep.operation.description?.toLowerCase().includes(q) ||
            ep.method.toLowerCase().includes(q)
        ),
      }))
      .filter((ctrl) => ctrl.endpoints.length > 0);
  }, [controllers, searchQuery]);

  // Auto-expand controllers with search results
  useEffect(() => {
    if (searchQuery.trim()) {
      const names = filteredControllers.map((c) => c.name);
      useStore.getState().setExpandedControllers(names);
    }
  }, [searchQuery]);

  const sidebarWidth = showFullSidebar ? 'w-64' : isTablet ? 'w-16' : 'w-0';

  return (
    <>
      <aside
        className={`fixed left-0 top-12 h-[calc(100vh-48px)] bg-slate-900 border-r border-slate-700 transition-all duration-300 overflow-hidden z-40 ${sidebarWidth}`}
        style={{ transitionTimingFunction: 'cubic-bezier(0.4, 0, 0.2, 1)' }}
      >
        {showFullSidebar ? (
          <div className="h-full overflow-y-auto scrollbar-thin">
            <nav className="py-2">
              {filteredControllers.map((ctrl) => {
                const Icon = ICON_MAP[ctrl.icon] || Circle;
                const isExpanded = expandedControllers.includes(ctrl.name);
                const hasActiveEndpoint = ctrl.endpoints.some((ep) => ep.id === activeEndpointId);

                return (
                  <div key={ctrl.name} className="mb-0.5">
                    {/* Controller Header */}
                    <button
                      onClick={() => toggleController(ctrl.name)}
                      className={`w-full flex items-center gap-2 px-4 py-2.5 text-left hover:bg-slate-800/50 transition-colors ${
                        hasActiveEndpoint ? 'bg-slate-800/30' : ''
                      }`}
                    >
                      <Icon size={16} className="text-slate-400 flex-shrink-0" />
                      <span className="flex-1 text-sm font-semibold text-slate-200 truncate">
                        {ctrl.displayName}
                      </span>
                      <span className="text-[11px] font-mono text-slate-500 bg-slate-800 px-1.5 py-0.5 rounded-full">
                        {ctrl.endpoints.length}
                      </span>
                      <span className="text-slate-500">
                        {isExpanded ? <ChevronDown size={14} /> : <ChevronRightIcon size={14} />}
                      </span>
                    </button>

                    {/* Endpoints */}
                    {isExpanded && (
                      <div className="pb-1">
                        {ctrl.endpoints.map((ep) => {
                          const isActive = ep.id === activeEndpointId;
                          return (
                            <button
                              key={ep.id}
                              onClick={() => handleEndpointClick(ep)}
                              className={`w-full flex items-center gap-2 pl-11 pr-3 py-1.5 text-left transition-all ${
                                isActive
                                  ? 'bg-blue-500/8 border-l-[3px] border-l-blue-500'
                                  : 'border-l-[3px] border-l-transparent hover:bg-slate-800/50'
                              }`}
                            >
                              <MethodBadge method={ep.method} size="sm" />
                              <span className={`text-xs font-mono truncate ${isActive ? 'text-slate-100' : 'text-slate-400'}`}>
                                {ep.path.replace('/api/v1/', '/')}
                              </span>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </nav>
          </div>
        ) : isTablet && sidebarOpen ? (
          // Icon-only mode for tablet
          <div className="h-full overflow-y-auto py-2">
            {filteredControllers.map((ctrl) => {
              const Icon = ICON_MAP[ctrl.icon] || Circle;
              const hasActive = ctrl.endpoints.some((ep) => ep.id === activeEndpointId);
              return (
                <button
                  key={ctrl.name}
                  onClick={() => {
                    setActiveController(ctrl.name);
                    const ep = ctrl.endpoints[0];
                    if (ep) handleEndpointClick(ep);
                  }}
                  className={`w-full flex justify-center py-3 transition-colors ${
                    hasActive ? 'text-blue-400' : 'text-slate-400 hover:text-slate-200'
                  }`}
                  title={ctrl.displayName}
                >
                  <Icon size={20} />
                </button>
              );
            })}
          </div>
        ) : null}

        {/* Collapse button - only on desktop */}
        {isDesktop && (
          <button
            onClick={toggleSidebar}
            className="absolute -right-3 top-4 w-6 h-6 bg-slate-800 border border-slate-700 rounded-full flex items-center justify-center hover:bg-blue-500/15 hover:border-blue-500/30 transition-colors z-50"
          >
            {sidebarOpen ? (
              <ChevronLeft size={14} className="text-slate-400" />
            ) : (
              <ChevronRight size={14} className="text-slate-400" />
            )}
          </button>
        )}
      </aside>

      {/* Mobile overlay */}
      {!isDesktop && sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 lg:hidden"
          onClick={toggleSidebar}
        />
      )}
    </>
  );
};

export default Sidebar;
